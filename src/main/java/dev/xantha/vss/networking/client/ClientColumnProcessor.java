package dev.xantha.vss.networking.client;

import dev.xantha.vss.api.VoxelColumnData;
import dev.xantha.vss.common.VSSLogger;
import dev.xantha.vss.config.VSSClientConfig;
import dev.xantha.vss.networking.client.ClientColumnTransferAssembler.AssembledColumn;
import dev.xantha.vss.networking.client.ClientColumnTransferAssembler.FailedTransfer;
import dev.xantha.vss.networking.client.ClientColumnTransferAssembler.OfferResult;
import dev.xantha.vss.networking.payloads.VoxelColumnS2CPayload;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** VSS MIT overlay: downstream ingestion pacing; preserve transfer/session semantics. */
final class ClientColumnProcessor {
    static volatile ClientColumnProcessor activeProcessor;
    static final int MAX_QUEUED_COLUMNS = 1024;
    static final long MAX_QUEUED_BYTES = 32L * 1024L * 1024L;
    private static final int MAX_COLUMNS_PER_DRAIN = 64;
    private static final int MAX_SECTIONS_DISPATCHED_PER_DRAIN = 768;
    private static final int MAX_SECTIONS_PER_COLUMN = 255;
    private static final long TRANSFER_IDLE_TIMEOUT_NANOS = dev.xantha.vss.common.VSSConstants.COLUMN_QUEUE_IDLE_TIMEOUT_NANOS;
    private static final long DROP_WARN_INTERVAL_MS = 5000L;

    private final ClientColumnTransferAssembler assembler =
            new ClientColumnTransferAssembler(MAX_QUEUED_COLUMNS, MAX_QUEUED_BYTES);
    private final ConcurrentLinkedQueue<AssembledColumn> priorityColumnQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<AssembledColumn> columnQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueSize = new AtomicInteger();
    private final AtomicInteger priorityQueueSize = new AtomicInteger();
    private final AtomicLong queueBytes = new AtomicLong();
    private final AtomicLong columnsDropped = new AtomicLong();
    private final AtomicBoolean processing = new AtomicBoolean();
    private final AtomicInteger sessionEpoch = new AtomicInteger();
    // Epoch, queue publication/removal and accounting share one short critical section.
    // Never acquire ClientLodBackpressure's monitor while holding this lock.
    private final Object queueLock = new Object();
    private volatile ExecutorService executor;
    private volatile boolean shuttingDown = true;
    private volatile long lastDropWarnMs;

    void beginSession() {
        synchronized (queueLock) {
            sessionEpoch.incrementAndGet();
            clearQueue();
            processing.set(false);
            ExecutorService currentExecutor = executor;
            if (currentExecutor == null || currentExecutor.isShutdown() || currentExecutor.isTerminated()) {
                executor = createExecutor();
            }
            shuttingDown = false;
        }
        activeProcessor = this;
        ClientLodBackpressure.reset();
    }

    boolean offer(
            VoxelColumnS2CPayload payload,
            boolean priority,
            boolean replaceMissingSections) {
        OfferResult result = null;
        synchronized (queueLock) {
            if (!shuttingDown) {
                result = assembler.offer(
                        payload,
                        priority,
                        replaceMissingSections,
                        queueSize.get(),
                        queueBytes.get(),
                        System.nanoTime());
                switch (result.status()) {
                    case ACCEPTED -> {
                        return true;
                    }
                    case COMPLETED -> {
                        AssembledColumn column = result.column();
                        if (column.priority()) {
                            priorityColumnQueue.add(column);
                            priorityQueueSize.incrementAndGet();
                        } else {
                            columnQueue.add(column);
                        }
                        queueSize.incrementAndGet();
                        queueBytes.addAndGet(column.retainedBytes());
                        return true;
                    }
                    case REJECTED -> {
                        // Notify outside the queue lock; callbacks may enter the request manager.
                    }
                    default -> throw new IllegalStateException("Unhandled transfer offer result: " + result.status());
                }
            }
        }
        if (result != null) recordDrop();
        fail(result == null ? FailedTransfer.from(payload) : result.failedTransfer());
        return false;
    }

    void scheduleProcessing(boolean serverEnabled) {
        if (shuttingDown) {
            return;
        }
        expireIdleTransfers();
        if (!serverEnabled || !VSSClientConfig.CONFIG.receiveServerLods) {
            clearQueue();
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            clearQueue();
            return;
        }
        if (priorityColumnQueue.isEmpty() && columnQueue.isEmpty()) {
            return;
        }

        if (VSSClientConfig.CONFIG.offThreadSectionProcessing) {
            int epoch;
            ExecutorService currentExecutor;
            synchronized (queueLock) {
                currentExecutor = executor;
                if (shuttingDown || processing.get() || currentExecutor == null || currentExecutor.isShutdown()) return;
                epoch = sessionEpoch.get();
                processing.set(true);
            }
            try {
                currentExecutor.execute(() -> {
                    try {
                        drainColumnQueue(level, epoch);
                    } finally {
                        finishProcessing(epoch);
                    }
                });
            } catch (Exception e) {
                finishProcessing(epoch);
            }
        } else {
            drainColumnQueue(level, sessionEpoch.get());
        }
    }

    private void drainColumnQueue(ClientLevel level, int epoch) {
        Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
        AssembledColumn column;
        int processedColumns = 0;
        int dispatchedSections = 0;
        while (processedColumns < MAX_COLUMNS_PER_DRAIN
                && dispatchedSections < MAX_SECTIONS_DISPATCHED_PER_DRAIN
                && !Thread.currentThread().isInterrupted()
                && sessionEpoch.get() == epoch
                && (column = pollQueuedColumn(epoch)) != null) {
            if (shuttingDown
                    || !VSSClientNetworking.isClientLodSessionActive()
                    || !level.dimension().equals(column.dimension())) {
                continue;
            }

            try {
                VoxelColumnData columnData = decodeColumn(column, biomeRegistry);
                if (sessionEpoch.get() != epoch || shuttingDown) {
                    return;
                }
                VSSClientNetworking.processAssembledColumn(level, column, columnData);
                processedColumns++;
                dispatchedSections += columnData.sections().length;
            } catch (Exception e) {
                VSSLogger.error("Failed to process voxel column transfer " + column.transferId()
                        + " at " + column.chunkX() + "," + column.chunkZ(), e);
                fail(new FailedTransfer(
                        column.requestId(),
                        column.transferId(),
                        column.dimension(),
                        column.chunkX(),
                        column.chunkZ()));
            }
        }
    }

    static VoxelColumnData decodeColumn(
            AssembledColumn column,
            Registry<Biome> biomeRegistry) {
        ArrayList<VoxelColumnData.SectionData> decodedSections = new ArrayList<>();
        Set<Integer> sectionYs = new HashSet<>();
        for (VoxelColumnS2CPayload part : column.parts()) {
            byte[] decompressed = part.decompressedSections();
            if (decompressed == null || decompressed.length == 0) {
                throw new IllegalArgumentException("Empty serialized section body");
            }
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(decompressed));
            try {
                int sectionCount = buf.readVarInt();
                if (sectionCount < 0 || decodedSections.size() + sectionCount > MAX_SECTIONS_PER_COLUMN) {
                    throw new IllegalArgumentException("Invalid assembled section count");
                }
                for (int i = 0; i < sectionCount; i++) {
                    int sectionY = buf.readByte();
                    if (!sectionYs.add(sectionY)) {
                        throw new IllegalArgumentException("Duplicate section " + sectionY);
                    }
                    LevelChunkSection section = new LevelChunkSection(biomeRegistry);
                    section.read(buf);

                    DataLayer blockLight = null;
                    if (buf.readBoolean()) {
                        byte[] light = new byte[DataLayer.SIZE];
                        buf.readBytes(light);
                        blockLight = new DataLayer(light);
                    }

                    DataLayer skyLight = null;
                    if (buf.readBoolean()) {
                        byte[] light = new byte[DataLayer.SIZE];
                        buf.readBytes(light);
                        skyLight = new DataLayer(light);
                    }
                    decodedSections.add(new VoxelColumnData.SectionData(
                            sectionY,
                            section,
                            blockLight,
                            skyLight));
                }
                if (buf.isReadable()) {
                    throw new IllegalArgumentException("Trailing bytes in LOD transfer part " + part.partIndex());
                }
            } finally {
                buf.release();
            }
        }

        int[] replacementSectionYs = column.finalPart().replacementSectionYs();
        if (replacementSectionYs.length != sectionYs.size()) {
            throw new IllegalArgumentException("Replacement manifest does not match assembled sections");
        }
        for (int sectionY : replacementSectionYs) {
            if (!sectionYs.contains(sectionY)) {
                throw new IllegalArgumentException("Replacement manifest references missing section " + sectionY);
            }
        }

        return new VoxelColumnData(
                decodedSections.toArray(VoxelColumnData.SectionData[]::new),
                column.columnTimestamp(),
                column.replaceMissingSections(),
                replacementSectionYs,
                true);
    }

    void invalidatePositions(ResourceKey<Level> dimension, long[] packedPositions) {
        synchronized (queueLock) {
            assembler.invalidatePositions(dimension, packedPositions);
            removeQueuedPositions(priorityColumnQueue, dimension, packedPositions, true);
            removeQueuedPositions(columnQueue, dimension, packedPositions, false);
        }
    }

    void clearPendingTransfers() {
        synchronized (queueLock) {
            sessionEpoch.incrementAndGet();
            clearQueue();
            processing.set(false);
        }
        if (activeProcessor == this) ClientLodBackpressure.reset();
    }

    void shutdown() {
        ExecutorService old;
        synchronized (queueLock) {
            shuttingDown = true;
            sessionEpoch.incrementAndGet();
            old = executor;
            clearQueue();
            processing.set(false);
            executor = null;
        }
        if (activeProcessor == this) {
            activeProcessor = null;
            ClientLodBackpressure.reset();
        }
        if (old != null) {
            old.shutdownNow();
        }
        VSSLogger.debug("Stopped VSS column processor and cleared queued LOD columns");
    }

    boolean isActive() {
        return !shuttingDown;
    }

    int getQueuedCount() {
        return queueSize.get() + assembler.activeLogicalColumns();
    }

    long getQueuedBytes() {
        return queueBytes.get() + assembler.activeBytes();
    }

    /** Called under the backpressure bridge lock before the manager's timeout sweep. */
    void refreshQueuedRequests(ClientRequestTracker tracker,long nowNanos,Map<Integer,Long> refreshed) {
        int epoch=sessionEpoch.get();
        Set<Integer> queued=new HashSet<>();
        for(var queue:List.of(priorityColumnQueue,columnQueue)) {
            for(AssembledColumn column:queue) {
                if(shuttingDown || sessionEpoch.get()!=epoch) { refreshed.clear();return; }
                int requestId=column.requestId();
                long packed=dev.xantha.vss.common.PositionUtil.packPosition(column.chunkX(),column.chunkZ());
                if(requestId<0 || !tracker.matches(requestId,packed))continue;
                queued.add(requestId);
                Long last=refreshed.get(requestId);
                if(last==null || nowNanos-last>=1_000_000_000L) {
                    tracker.refreshDeadline(requestId,30_000_000_000L,nowNanos);
                    refreshed.put(requestId,nowNanos);
                }
            }
        }
        refreshed.keySet().retainAll(queued);
    }

    boolean hasPendingWork() {
        return processing.get() || getQueuedCount() > 0;
    }

    long getColumnsDropped() {
        return columnsDropped.get();
    }

    void resetStats() {
        columnsDropped.set(0L);
        lastDropWarnMs = 0L;
    }

    private void expireIdleTransfers() {
        List<FailedTransfer> expired = assembler.expireIdle(
                System.nanoTime(),
                TRANSFER_IDLE_TIMEOUT_NANOS);
        for (FailedTransfer transfer : expired) {
            recordDrop();
            fail(transfer);
        }
    }

    void onColumnQueued(int requestId) {
        assembler.refreshRequest(requestId, System.nanoTime());
    }

    private void clearQueue() {
        synchronized (queueLock) {
            assembler.clear();
            priorityColumnQueue.clear();
            columnQueue.clear();
            queueSize.set(0);
            priorityQueueSize.set(0);
            queueBytes.set(0L);
        }
    }

    private AssembledColumn pollQueuedColumn(int epoch) {
        if (ClientLodBackpressure.pauseDrain()) return null;
        synchronized (queueLock) {
            // A session can change while the worker waits on downstream pacing.
            // Old workers must not take a new session's column or its accounting.
            if (sessionEpoch.get() != epoch || shuttingDown) return null;
            AssembledColumn column = priorityColumnQueue.poll();
            if (column != null) {
                priorityQueueSize.updateAndGet(value -> Math.max(0, value - 1));
            } else column = columnQueue.poll();
            if (column != null) removeQueuedAccounting(column);
            return column;
        }
    }

    private void finishProcessing(int epoch) {
        synchronized (queueLock) {
            if (sessionEpoch.get() == epoch) processing.set(false);
        }
    }

    private void removeQueuedAccounting(AssembledColumn column) {
        queueSize.updateAndGet(value -> Math.max(0, value - 1));
        queueBytes.updateAndGet(value -> Math.max(0L, value - column.retainedBytes()));
    }

    private void removeQueuedPositions(
            ConcurrentLinkedQueue<AssembledColumn> queue,
            ResourceKey<Level> dimension,
            long[] packedPositions,
            boolean priority) {
        if (packedPositions == null || packedPositions.length == 0) {
            return;
        }
        for (AssembledColumn column : queue) {
            if (!dimension.equals(column.dimension()) || !containsPosition(packedPositions, column.chunkX(), column.chunkZ())) {
                continue;
            }
            if (queue.remove(column)) {
                removeQueuedAccounting(column);
                if (priority) {
                    priorityQueueSize.updateAndGet(value -> Math.max(0, value - 1));
                }
            }
        }
    }

    private void recordDrop() {
        long dropped = columnsDropped.incrementAndGet();
        long now = System.currentTimeMillis();
        if (now - lastDropWarnMs > DROP_WARN_INTERVAL_MS) {
            lastDropWarnMs = now;
            VSSLogger.warn("Column transfer queue rejected or expired data ("
                    + MAX_QUEUED_COLUMNS + " columns, "
                    + (MAX_QUEUED_BYTES / 1024L / 1024L) + " MiB), "
                    + dropped + " transfers dropped total");
        }
    }

    private static boolean containsPosition(long[] positions, int chunkX, int chunkZ) {
        long packed = ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
        for (long position : positions) {
            if (position == packed) {
                return true;
            }
        }
        return false;
    }

    private static void fail(FailedTransfer transfer) {
        if (transfer != null) {
            VSSClientNetworking.onColumnTransferFailed(
                    transfer.requestId(),
                    transfer.transferId(),
                    transfer.dimension(),
                    transfer.chunkX(),
                    transfer.chunkZ());
        }
    }

    private static ExecutorService createExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "VSS-ColumnProcessor");
            thread.setDaemon(true);
            return thread;
        });
    }
}
