/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.chunk.LevelChunk
 *  net.minecraft.world.level.chunk.storage.ChunkStorage
 */
package dev.xantha.vss.networking.server.request;

import dev.xantha.vss.common.DiagnosticCounters;
import dev.xantha.vss.common.VSSLogger;
import dev.xantha.vss.common.processing.EncodedColumnData;
import dev.xantha.vss.common.processing.LoadedColumnData;
import dev.xantha.vss.config.VSSServerConfig;
import dev.xantha.vss.networking.VSSNetworking;
import dev.xantha.vss.networking.payloads.BatchResponseS2CPayload;
import dev.xantha.vss.networking.payloads.VoxelColumnS2CPayload;
import dev.xantha.vss.networking.server.VSSServerNetworking;
import dev.xantha.vss.networking.server.diagnostics.ServerRequestStats;
import dev.xantha.vss.networking.server.dirty.DirtyColumnBroadcaster;
import dev.xantha.vss.networking.server.generation.ChunkGenerationService;
import dev.xantha.vss.networking.server.runtime.DiskTaskRuntime;
import dev.xantha.vss.networking.server.state.PlayerRequestRegistry;
import dev.xantha.vss.networking.server.state.PlayerRequestState;
import dev.xantha.vss.networking.server.storage.ColumnLodCache;
import dev.xantha.vss.networking.server.storage.NbtSectionSerializer;
import dev.xantha.vss.networking.server.storage.PersistentColumnLodStore;
import dev.xantha.vss.networking.server.storage.PersistentColumnWriter;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkStorage;

public final class ColumnStorageReadPipeline {
    private final PlayerRequestRegistry playerRegistry;
    private final ChunkGenerationService generationService;
    private final ColumnLodCache columnCache;
    private final PersistentColumnLodStore persistentStore;
    private final PersistentColumnWriter persistentWriter;
    private final ServerRequestStats requestStats;
    private final DiskTaskRuntime diskRuntime;
    private final DiagnosticCounters generationDiagnostics = new DiagnosticCounters(VSSLogger::isDebugEnabled, 5000000000L);

    public ColumnStorageReadPipeline(PlayerRequestRegistry playerRegistry, ChunkGenerationService generationService, ColumnLodCache columnCache, PersistentColumnLodStore persistentStore, PersistentColumnWriter persistentWriter, ServerRequestStats requestStats, DiskTaskRuntime diskRuntime) {
        this.playerRegistry = playerRegistry;
        this.generationService = generationService;
        this.columnCache = columnCache;
        this.persistentStore = persistentStore;
        this.persistentWriter = persistentWriter;
        this.requestStats = requestStats;
        this.diskRuntime = diskRuntime;
    }

    public boolean submitLoadedColumn(ServerPlayer player, PlayerRequestState state, ServerLevel level, LevelChunk chunk, int requestId, int cx, int cz, long minimumTimestamp, boolean priority) {
        return this.generationService.submitLoadedColumn(player.getUUID(), state, requestId, level, chunk, cx, cz, minimumTimestamp, priority);
    }

    public void submitStorageRead(ServerPlayer player, PlayerRequestState state, int requestId, int cx, int cz, long columnTimestamp, long dirtyTimestamp, boolean preferLoadedColumn, boolean allowGeneration, boolean cacheProbe, boolean priority) {
        if (VSSServerNetworking.isServerStopping()) {
            state.clearRequest(requestId);
            return;
        }
        this.generationDiagnostics.record(cacheProbe ? "storageProbe" : (allowGeneration ? "storageGenerationAllowed" : "storageSyncOnly"));
        this.logGenerationDiagnostics();
        UUID playerId = player.getUUID();
        ServerLevel level = player.serverLevel();
        MinecraftServer server = player.server;
        DiskReadContext readContext = new DiskReadContext(VSSServerNetworking.lifecycleEpoch(), playerId, level, state, requestId, cx, cz, columnTimestamp, preferLoadedColumn, allowGeneration, cacheProbe, priority);
        DiskTaskRuntime.ReadKey readKey = new DiskTaskRuntime.ReadKey(level.dimension().location(), cx, cz);
        boolean submitted = this.diskRuntime.submitCoalescedRead(readKey, false, VSSServerConfig.CONFIG.diskReadQueueLimit, 0, () -> this.readPersistentColumn(server, readContext, dirtyTimestamp), storedData -> this.continueAfterPersistentRead(readContext, (PersistentColumnLodStore.Entry)storedData, dirtyTimestamp), e -> {
            readContext.requestState().clearRequest(readContext.requestId());
            ColumnStorageReadPipeline.sendBackpressured(player, readContext.requestId());
        });
        if (submitted) {
            this.requestStats.recordDiskReadSubmitted();
        }
    }

    private PersistentColumnLodStore.Entry readPersistentColumn(MinecraftServer server, DiskReadContext readContext, long dirtyTimestamp) {
        try {
            if (VSSServerNetworking.isLifecycleStale(readContext.lifecycleEpoch())) {
                return null;
            }
            ColumnLodCache.Entry cached = this.columnCache.get((ResourceKey<Level>)readContext.level().dimension(), readContext.cx(), readContext.cz());
            if (cached != null && cached.completeColumn() && cached.timestamp() >= dirtyTimestamp) {
                this.requestStats.recordPreloadReuse();
                return new PersistentColumnLodStore.Entry(cached.columnData());
            }
            PersistentColumnLodStore.Entry storedData = this.persistentStore.read(server, (ResourceKey<Level>)readContext.level().dimension(), readContext.cx(), readContext.cz(), dirtyTimestamp > 0L ? dirtyTimestamp : 0L);
            if (VSSServerNetworking.isLifecycleStale(readContext.lifecycleEpoch())) {
                return null;
            }
            return storedData;
        }
        catch (RejectedExecutionException e) {
            throw e;
        }
        catch (Exception e) {
            VSSLogger.warn("Failed to finish VSS disk read at " + readContext.cx() + ", " + readContext.cz() + ": " + e.getMessage());
            return null;
        }
    }

    private void continueAfterPersistentRead(DiskReadContext readContext, PersistentColumnLodStore.Entry storedData, long dirtyTimestamp) {
        if (readContext.requestState().consumeCancelled(readContext.requestId()) || !readContext.requestState().isActiveRequest(readContext.requestId())) {
            return;
        }
        ColumnLodCache.Entry cached = this.columnCache.get((ResourceKey<Level>)readContext.level().dimension(), readContext.cx(), readContext.cz());
        if (cached != null && cached.completeColumn() && cached.timestamp() >= dirtyTimestamp) {
            this.scheduleDiskReadFinish(readContext, storedData, DiskNbtReadResult.cached(cached.columnData()));
            return;
        }
        if (storedData != null || readContext.preferLoadedColumn() || !ColumnStorageReadPipeline.shouldReadExistingChunkNbt(VSSServerConfig.CONFIG.enableChunkNbtColumnSync, readContext.allowGeneration(), VSSServerConfig.CONFIG.enableChunkGeneration, readContext.cacheProbe())) {
            this.scheduleDiskReadFinish(readContext, storedData, DiskNbtReadResult.empty());
            return;
        }
        VSSServerConfig config = VSSServerConfig.CONFIG;
        boolean submitted = this.diskRuntime.<DiskNbtReadResult>submitCoalescedNbtRead(new DiskTaskRuntime.ReadKey(readContext.level().dimension().location(), readContext.cx(), readContext.cz()), config.maxConcurrentNbtReads, config.nbtReadQueueLimit, config.diskReadQueueLimit, () -> !VSSServerNetworking.isLifecycleStale(readContext.lifecycleEpoch()) && readContext.requestState().isActiveRequest(readContext.requestId()), completion -> this.startChunkNbtRead(readContext, completion), result -> this.scheduleDiskReadFinish(readContext, storedData, result), error -> {
            readContext.requestState().clearRequest(readContext.requestId());
            ServerPlayer player = readContext.level().getServer().getPlayerList().getPlayer(readContext.playerId());
            if (player != null) {
                ColumnStorageReadPipeline.sendBackpressured(player, readContext.requestId());
            }
        });
        if (!submitted) {
            readContext.requestState().clearRequest(readContext.requestId());
        }
    }

    private void startChunkNbtRead(DiskReadContext readContext, DiskTaskRuntime.AsyncReadCompletion<DiskNbtReadResult> completion) {
        if (VSSServerNetworking.isLifecycleStale(readContext.lifecycleEpoch()) || !completion.hasActiveListeners()) {
            this.completeNbtRead(completion, DiskNbtReadResult.empty());
            return;
        }
        try {
            NbtSectionSerializer.readSectionsAsync((ChunkStorage)readContext.level().getChunkSource().chunkMap, readContext.cx(), readContext.cz(), VSSServerConfig.CONFIG.diskReadTimeoutMillis).whenComplete((optionalTag, error) -> {
                if (error != null) {
                    this.completeNbtRead(completion, VSSServerNetworking.isLifecycleStale(readContext.lifecycleEpoch()) ? DiskNbtReadResult.empty() : DiskNbtReadResult.failure());
                    return;
                }
                completion.execute(() -> {
                    try {
                        if (VSSServerNetworking.isLifecycleStale(readContext.lifecycleEpoch()) || !completion.hasActiveListeners()) {
                            this.completeNbtRead(completion, DiskNbtReadResult.empty());
                            return;
                        }
                        LoadedColumnData rawDiskData = NbtSectionSerializer.serializeTag(readContext.level(), readContext.cx(), readContext.cz(), optionalTag);
                        EncodedColumnData encoded = rawDiskData != null && rawDiskData.completeColumn() && rawDiskData.sectionBytes() != null && rawDiskData.sizeBytes() > 0 ? EncodedColumnData.encode(rawDiskData, 0L) : null;
                        this.completeNbtRead(completion, encoded == null ? DiskNbtReadResult.miss() : DiskNbtReadResult.hit(encoded));
                    }
                    catch (Exception exception) {
                        VSSLogger.warn("Failed to transcode chunk NBT at " + readContext.cx() + ", " + readContext.cz() + ": " + exception.getMessage());
                        this.completeNbtRead(completion, DiskNbtReadResult.failure());
                    }
                });
            });
        }
        catch (Exception exception) {
            this.completeNbtRead(completion, DiskNbtReadResult.failure());
        }
    }

    private void completeNbtRead(DiskTaskRuntime.AsyncReadCompletion<DiskNbtReadResult> completion, DiskNbtReadResult result) {
        if (!completion.complete(result)) {
            return;
        }
        if (result.outcome() == NbtReadOutcome.HIT) {
            this.diskRuntime.recordNbtReadHit();
        } else if (result.outcome() == NbtReadOutcome.MISS) {
            this.diskRuntime.recordNbtReadMiss();
        } else if (result.outcome() == NbtReadOutcome.FAILED) {
            this.diskRuntime.recordNbtReadFailure();
        }
    }

    private void scheduleDiskReadFinish(DiskReadContext readContext, PersistentColumnLodStore.Entry storedData, DiskNbtReadResult result) {
        try {
            me.cortex.voxy.hxz.server.ServerTickGuard.complete(readContext.level().getServer(),
                () -> this.finishDiskRead(readContext, storedData, result.columnData(), result.failed()),
                () -> readContext.requestState().clearRequest(readContext.requestId()));
        }
        catch (RejectedExecutionException error) {
            readContext.requestState().clearRequest(readContext.requestId());
        }
    }

    private static boolean shouldReadExistingChunkNbt(boolean allowGeneration) {
        VSSServerConfig config = VSSServerConfig.CONFIG;
        return ColumnStorageReadPipeline.shouldReadExistingChunkNbt(config.enableChunkNbtColumnSync, allowGeneration, config.enableChunkGeneration);
    }

    public static boolean shouldReadExistingChunkNbt(boolean enableChunkNbtColumnSync, boolean allowGeneration, boolean enableChunkGeneration) {
        return enableChunkNbtColumnSync || !allowGeneration || !enableChunkGeneration;
    }

    static boolean shouldReadExistingChunkNbt(boolean enableChunkNbtColumnSync, boolean allowGeneration, boolean enableChunkGeneration, boolean cacheProbe) {
        if (cacheProbe) {
            return false;
        }
        return ColumnStorageReadPipeline.shouldReadExistingChunkNbt(enableChunkNbtColumnSync, allowGeneration, enableChunkGeneration);
    }

    private void finishDiskRead(DiskReadContext readContext, PersistentColumnLodStore.Entry storedData, EncodedColumnData diskData, boolean readFailed) {
        LevelChunk chunk;
        if (VSSServerNetworking.isLifecycleStale(readContext.lifecycleEpoch())) {
            return;
        }
        if (readFailed) {
            this.requestStats.recordDiskReadFailure();
        }
        ServerLevel level = readContext.level();
        PlayerRequestState requestState = readContext.requestState();
        int requestId = readContext.requestId();
        int cx = readContext.cx();
        int cz = readContext.cz();
        long columnTimestamp = readContext.columnTimestamp();
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(readContext.playerId());
        if (player == null || !this.playerRegistry.isCurrent(readContext.playerId(), requestState) || requestState.consumeCancelled(requestId) || !requestState.isActiveRequest(requestId)) {
            return;
        }
        if (!player.serverLevel().dimension().equals(level.dimension())) {
            requestState.clearRequest(requestId);
            return;
        }
        if (!VSSServerNetworking.isColumnStillRelevant(player, (ResourceKey<Level>)level.dimension(), cx, cz)) {
            requestState.clearRequest(requestId);
            return;
        }
        long latestDirtyTimestamp = DirtyColumnBroadcaster.latestDirtyTimestamp((ResourceKey<Level>)level.dimension(), cx, cz);
        long effectiveColumnTimestamp = Math.max(columnTimestamp, latestDirtyTimestamp);
        if (storedData != null && storedData.columnData() != null && storedData.columnData().completeColumn() && storedData.timestamp() >= latestDirtyTimestamp) {
            this.sendStoredColumn(readContext, storedData, player, effectiveColumnTimestamp);
            return;
        }
        if (readContext.preferLoadedColumn() && (chunk = level.getChunkSource().getChunkNow(cx, cz)) != null) {
            if (this.submitLoadedColumn(player, requestState, level, chunk, requestId, cx, cz, effectiveColumnTimestamp, readContext.priority())) {
                return;
            }
            requestState.clearRequest(requestId);
            ColumnStorageReadPipeline.sendRateLimited(player, requestId);
            return;
        }
        if (latestDirtyTimestamp > columnTimestamp) {
            this.handleMissingDiskColumn(readContext, player, effectiveColumnTimestamp);
            return;
        }
        if (diskData == null || !diskData.hasBody() || !diskData.completeColumn()) {
            this.handleMissingDiskColumn(readContext, player, effectiveColumnTimestamp);
            return;
        }
        this.sendDiskColumn(readContext, player, diskData, effectiveColumnTimestamp);
    }

    private void sendStoredColumn(DiskReadContext readContext, PersistentColumnLodStore.Entry storedData, ServerPlayer player, long columnTimestamp) {
        ServerLevel level = readContext.level();
        EncodedColumnData columnData = storedData.columnData().withColumnStamp(Math.max(storedData.timestamp(), columnTimestamp));
        this.requestStats.recordDiskReadHit();
        VSSServerNetworking.queueColumn(player, readContext.requestState(), new VoxelColumnS2CPayload(readContext.requestId(), (ResourceKey<Level>)level.dimension(), columnData), readContext.priority());
        this.columnCache.put((ResourceKey<Level>)level.dimension(), columnData);
    }

    private void handleMissingDiskColumn(DiskReadContext readContext, ServerPlayer player, long minimumTimestamp) {
        this.requestStats.recordDiskReadMiss();
        this.generationDiagnostics.record(readContext.cacheProbe() ? "missProbe" : (!readContext.allowGeneration() ? "missGenerationNotRequested" : (!VSSServerConfig.CONFIG.enableChunkGeneration ? "missServerGenerationDisabled" : "missSubmitGeneration")));
        this.logGenerationDiagnostics();
        if (!readContext.cacheProbe() && readContext.allowGeneration() && VSSServerConfig.CONFIG.enableChunkGeneration) {
            this.submitGeneration(player, readContext.requestState(), readContext.level(), readContext.requestId(), readContext.cx(), readContext.cz(), minimumTimestamp, readContext.priority());
            return;
        }
        readContext.requestState().clearRequest(readContext.requestId());
        ColumnStorageReadPipeline.sendNotGenerated(player, readContext.requestId());
    }

    private void sendDiskColumn(DiskReadContext readContext, ServerPlayer player, EncodedColumnData diskData, long columnTimestamp) {
        ServerLevel level = readContext.level();
        this.requestStats.recordDiskReadHit();
        EncodedColumnData encodedDiskData = diskData.withColumnStamp(columnTimestamp);
        VSSServerNetworking.queueColumn(player, readContext.requestState(), new VoxelColumnS2CPayload(readContext.requestId(), (ResourceKey<Level>)level.dimension(), encodedDiskData), readContext.priority());
        this.columnCache.put((ResourceKey<Level>)level.dimension(), encodedDiskData);
        this.persistentWriter.write(level.getServer(), (ResourceKey<Level>)level.dimension(), encodedDiskData);
    }

    private void submitGeneration(ServerPlayer player, PlayerRequestState state, ServerLevel level, int requestId, int cx, int cz, long minimumTimestamp, boolean priority) {
        if (VSSServerNetworking.isServerStopping()) {
            state.clearRequest(requestId);
            return;
        }
        boolean accepted = this.generationService.submitGeneration(player.getUUID(), state, requestId, level, cx, cz, minimumTimestamp, priority);
        this.generationDiagnostics.record(accepted ? "generationAccepted" : "generationRejected");
        if (!accepted) {
            state.clearRequest(requestId);
            ColumnStorageReadPipeline.sendRateLimited(player, requestId);
            return;
        }
        ColumnStorageReadPipeline.sendGenerationQueued(player, requestId);
    }

    private void logGenerationDiagnostics() {
        String events = this.generationDiagnostics.poll(System.nanoTime());
        if (events != null) {
            VSSLogger.debug("VSS generation diagnostic server v1: scope=storagePipelineAllPlayers, generationEnabled=" + VSSServerConfig.CONFIG.enableChunkGeneration + ", " + events);
        }
    }

    private static void sendRateLimited(ServerPlayer player, int requestId) {
        VSSNetworking.sendToPlayer(player, new BatchResponseS2CPayload(new byte[]{0}, new int[]{requestId}, 1));
    }

    private static void sendBackpressured(ServerPlayer player, int requestId) {
        VSSNetworking.sendToPlayer(player, new BatchResponseS2CPayload(new byte[]{3}, new int[]{requestId}, 1));
    }

    private static void sendGenerationQueued(ServerPlayer player, int requestId) {
        VSSNetworking.sendToPlayer(player, new BatchResponseS2CPayload(new byte[]{4}, new int[]{requestId}, 1));
    }

    private static void sendNotGenerated(ServerPlayer player, int requestId) {
        VSSNetworking.sendToPlayer(player, new BatchResponseS2CPayload(new byte[]{2}, new int[]{requestId}, 1));
    }

    private record DiskReadContext(long lifecycleEpoch, UUID playerId, ServerLevel level, PlayerRequestState requestState, int requestId, int cx, int cz, long columnTimestamp, boolean preferLoadedColumn, boolean allowGeneration, boolean cacheProbe, boolean priority) {
    }

    private record DiskNbtReadResult(EncodedColumnData columnData, NbtReadOutcome outcome) {
        static DiskNbtReadResult empty() {
            return new DiskNbtReadResult(null, NbtReadOutcome.CANCELLED);
        }

        static DiskNbtReadResult cached(EncodedColumnData columnData) {
            return new DiskNbtReadResult(columnData, NbtReadOutcome.CANCELLED);
        }

        static DiskNbtReadResult hit(EncodedColumnData columnData) {
            return new DiskNbtReadResult(columnData, NbtReadOutcome.HIT);
        }

        static DiskNbtReadResult miss() {
            return new DiskNbtReadResult(null, NbtReadOutcome.MISS);
        }

        static DiskNbtReadResult failure() {
            return new DiskNbtReadResult(null, NbtReadOutcome.FAILED);
        }

        boolean failed() {
            return this.outcome == NbtReadOutcome.FAILED;
        }
    }

    private static enum NbtReadOutcome {
        HIT,
        MISS,
        FAILED,
        CANCELLED;

    }
}

