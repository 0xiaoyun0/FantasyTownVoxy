package dev.xantha.vss.networking.client;

import dev.xantha.vss.common.PositionUtil;
import dev.xantha.vss.networking.payloads.SessionConfigS2CPayload;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.Semaphore;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.minecraft.world.level.Level;

/** Real request tracker -> timeout sweep -> retry scan -> column acceptance after a long ingest pause. */
public final class QueuedColumnTimeoutTest {
    private static Field field(Class<?> type, String name) throws Exception {
        var f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static List<?> sweep(ClientRequestTracker tracker, long now) throws Exception {
        try {
            return (List<?>) ClientLodBackpressure.class.getMethod("drainTimedOut", Object.class, long.class)
                    .invoke(null, tracker, now);
        } catch (NoSuchMethodException baseline) {
            return tracker.drainTimedOut(now);
        }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        var cancelled = new AtomicInteger();
        var tracker = new ClientRequestTracker(id -> cancelled.incrementAndGet());
        var manager = new LodRequestManager("hxz-timeout-test", tracker);
        field(LodRequestManager.class, "lastDimension").set(manager, Level.OVERWORLD);
        field(LodRequestManager.class, "sessionConfig").set(manager,
                new SessionConfigS2CPayload(1, true, 128, 0, 0, 0, 0, 0, 128, false, 0L, 1L));
        // Presence storage is in-memory for this seam; native reconnect tests cover the real LOD database.
        field(ClientLodPresenceCache.class, "loaded").setBoolean(null, true);
        field(ClientLodPresenceCache.class, "updatesSinceSave").setInt(null, 0);
        field(ClientLodPresenceCache.class, "lastSaveNanos").setLong(null, System.nanoTime());
        var processor = new ClientColumnProcessor(); processor.beginSession();
        var ingest = new VoxelIngestService(new ServiceManager(count -> {}));
        Object service = field(VoxelIngestService.class, "service").get(ingest);
        var pending = (Semaphore) field(service.getClass(), "tasks").get(service);
        ClientIngestBackpressureTest.install(ingest);
        pending.release(8192);
        try {
            long now = 1_000_000_000L, timeout = 30_000_000_000L;
            long packed = PositionUtil.packPosition(2, 3);
            int request = tracker.track(packed, false, false, timeout, now);
            int missing = tracker.track(PositionUtil.packPosition(20, 30), false, false, timeout, now);
            var column = new ClientColumnTransferAssembler.AssembledColumn(request, 123L, 2, 3,
                    Level.OVERWORLD, 100L, false, false, List.of(), 64);
            var queue = (Queue<ClientColumnTransferAssembler.AssembledColumn>) field(ClientColumnProcessor.class, "columnQueue").get(processor);
            queue.add(column);
            ((AtomicInteger) field(ClientColumnProcessor.class, "queueSize").get(processor)).set(1);
            ((AtomicLong) field(ClientColumnProcessor.class, "queueBytes").get(processor)).set(64L);
            check(ClientLodBackpressure.pauseDrain(), "Real Voxy backlog did not pause the decoder");

            // More than both the ordinary 30-second request deadline and 120-second assembler idle timeout.
            var timedOut = sweep(tracker, now + 180_000_000_000L);
            check(!tracker.matches(missing, PositionUtil.packPosition(20, 30)), "Never-received request was kept alive");
            pending.drainPermits();
            check(!ClientLodBackpressure.pauseRequests(), "Real request scheduler did not resume after ingest backlog cleared");
            boolean retried = !tracker.contains(packed);
            if (retried) tracker.track(packed, false, false, timeout, now + 180_000_000_000L);
            var dispatches = new AtomicInteger();
            var result = manager.processColumnIfCurrent(request, Level.OVERWORLD, 2, 3, 100L, new int[0],
                    () -> { dispatches.incrementAndGet(); return true; });
            System.out.println("paused=180s, retried=" + retried + ", result=" + result + ", dispatches=" + dispatches);
            check(!retried && result == LodRequestManager.ColumnProcessingResult.APPLIED && dispatches.get() == 1,
                    "Already-received complete LOD column was discarded after timeout/retry instead of reaching persistent ingest");
            check(timedOut.size() == 1 && cancelled.get() == 1, "Complete queue protection changed genuine server timeout handling");
            check(queue.peek() == column, "Timeout protection consumed or replaced the retained bytes");

            processor.clearPendingTransfers();
            int afterReset = tracker.track(PositionUtil.packPosition(2, 3), false, false, timeout, now + 200_000_000_000L);
            sweep(tracker, now + 400_000_000_000L);
            check(!tracker.matches(afterReset, packed), "World/session reset kept an old queue request lease alive");
            check(queue.isEmpty(), "Session reset retained stale column data");
            System.out.println("PASS full queued columns survive a 180s pause; unanswered requests and reset sessions still time out");
            newlyQueued(processor, queue);
            sustainedQueue(processor, queue);
        } finally { processor.shutdown(); field(VoxyCommon.class, "INSTANCE").set(null, null); }
    }

    private static void newlyQueued(ClientColumnProcessor processor,
            Queue<ClientColumnTransferAssembler.AssembledColumn> queue) throws Exception {
        processor.clearPendingTransfers();
        var tracker = new ClientRequestTracker(id -> {});
        long now = 500_000_000_000L;
        int first = tracker.track(PositionUtil.packPosition(1, 2), false, false, 30_000_000_000L, now);
        queue.add(new ClientColumnTransferAssembler.AssembledColumn(first, 10L, 1, 2, Level.OVERWORLD, 1L, false, false, List.of(), 64));
        sweep(tracker, now);
        int newcomer = tracker.track(PositionUtil.packPosition(3, 4), false, false, 1L, now + 1);
        queue.add(new ClientColumnTransferAssembler.AssembledColumn(newcomer, 11L, 3, 4, Level.OVERWORLD, 1L, false, false, List.of(), 64));
        sweep(tracker, now + 2);
        check(tracker.matches(newcomer, PositionUtil.packPosition(3, 4)),
                "A newly completed column expired during another column's refresh throttle window");
        System.out.println("PASS newly queued complete columns gain protection immediately within the refresh throttle interval");
    }

    @SuppressWarnings("unchecked")
    private static void sustainedQueue(ClientColumnProcessor processor,
            Queue<ClientColumnTransferAssembler.AssembledColumn> queue) throws Exception {
        processor.clearPendingTransfers();
        var tracker = new ClientRequestTracker(id -> {});
        var priority = (Queue<ClientColumnTransferAssembler.AssembledColumn>) field(ClientColumnProcessor.class, "priorityColumnQueue").get(processor);
        long start = 1_000_000_000_000L;
        for (int x = 0; x < 1024; x++) {
            int id = tracker.track(PositionUtil.packPosition(x, -3), false, false, 30_000_000_000L, start);
            var column = new ClientColumnTransferAssembler.AssembledColumn(id, 100L + x, x, -3, Level.OVERWORLD, 1L,
                    x % 2 == 0, false, List.of(), 64);
            (x % 2 == 0 ? priority : queue).add(column);
        }
        int maximumHeap = 0;
        for (int tick = 0; tick < 1200; tick++) {
            check(sweep(tracker, start + tick * 50_000_000L).isEmpty(), "Retained full queue expired during pressure");
            int heap = ((Queue<?>) field(ClientRequestTracker.class, "requestDeadlines").get(tracker)).size();
            maximumHeap = Math.max(maximumHeap, heap);
        }
        check(tracker.size() == 1024 && priority.size() == 512 && queue.size() == 512,
                "Timeout pacing changed retained queue contents or request accounting");
        // The first refresh deliberately shares the original timestamp; upstream keeps
        // both equal-deadline entries until the next revision, but must remain bounded.
        check(maximumHeap <= 4096, "Deadline tombstones grew beyond a bounded multiple of live requests: " + maximumHeap);
        me.cortex.voxy.hxz.HxzSettings.CONFIG.clientIngestBackpressure = false;
        try {
            check(sweep(tracker, start + 180_000_000_000L).size() == 1024,
                    "Disabling backpressure retained request leases instead of restoring original timeouts");
        } finally { me.cortex.voxy.hxz.HxzSettings.CONFIG.clientIngestBackpressure = true; }
        processor.clearPendingTransfers();
        System.out.println("PASS 1,024 queued columns across 1,200 sweeps: bounded deadline heap=" + maximumHeap + "; opt-out restores timeouts");
    }
}
