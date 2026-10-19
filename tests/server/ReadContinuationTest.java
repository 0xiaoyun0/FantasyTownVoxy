package dev.xantha.vss.networking.server.runtime;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;

/** Actual runtime call order: an async NBT read retains its lease while live reads fill the queue. */
public final class ReadContinuationTest {
    private static void await(CountDownLatch latch, String message) throws InterruptedException {
        if (!latch.await(3, TimeUnit.SECONDS)) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        int failures = 0;
        for (String scenario : new String[]{"saturated", "listeners", "cancelled", "shutdown", "queuedShutdown", "resetRunning", "decodeError", "racing"}) {
            try {
                switch (scenario) {
                    case "saturated" -> saturated();
                    case "listeners" -> listeners();
                    case "cancelled" -> cancelled();
                    case "shutdown" -> shutdown();
                    case "queuedShutdown" -> queuedShutdown();
                    case "resetRunning" -> resetRunning();
                    case "decodeError" -> decodeError();
                    case "racing" -> racing();
                }
            } catch (Throwable error) {
                failures++;
                System.err.println("FAIL " + scenario + ": " + error);
            }
        }
        if (failures != 0) throw new AssertionError(failures + " disk pipeline scenarios failed");
    }

    private static void drained(DiskTaskRuntime runtime) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while ((runtime.pendingReads() != 0 || runtime.snapshot().nbtReadsActive() != 0)
                && System.nanoTime() < deadline) Thread.sleep(1);
        if (runtime.pendingReads() != 0 || runtime.snapshot().nbtReadsActive() != 0)
            throw new AssertionError("Leaked read leases: " + runtime.pendingReads() + ", active NBT=" + runtime.snapshot().nbtReadsActive());
    }

    private static void saturated() throws Exception {
        DiskTaskRuntime runtime = new DiskTaskRuntime(1, 1, () -> 1, () -> true);
        CountDownLatch nbtStarted = new CountDownLatch(1);
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<DiskTaskRuntime.AsyncReadCompletion<String>> completion = new AtomicReference<>();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger decoded = new AtomicInteger();
        try {
            runtime.<String>submitCoalescedNbtRead(
                    new DiskTaskRuntime.ReadKey(ResourceLocation.parse("test:world"), 0, 0),
                    1, 64, 64, () -> true,
                    pending -> { completion.set(pending); nbtStarted.countDown(); },
                    data -> { if (!"exact-column".equals(data)) throw new AssertionError("Column changed"); delivered.countDown(); },
                    error -> rejected.incrementAndGet());
            await(nbtStarted, "NBT read did not start");
            if (!runtime.submitRead(64, () -> {
                blockerStarted.countDown();
                try { await(release, "Blocked disk was never released"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, error -> rejected.incrementAndGet())) throw new AssertionError("Blocker rejected");
            await(blockerStarted, "Disk did not block");
            for (int i = 0; i < 62; i++)
                if (!runtime.submitRead(64, () -> {}, error -> rejected.incrementAndGet()))
                    throw new AssertionError("Expected exactly 64 occupied leases");
            if (runtime.pendingReads() != 64) throw new AssertionError("Queue not saturated");
            boolean accepted = completion.get().execute(() -> {
                decoded.incrementAndGet();
                completion.get().complete("exact-column");
            });
            int peak = runtime.pendingReads();
            System.out.println((accepted ? "PASS" : "FAIL") + " saturated NBT continuation: accepted="
                    + accepted + ", pending=" + peak + ", rejected=" + rejected.get());
            if (!accepted) throw new AssertionError("Already-read NBT column was dropped while its own disk lease was still held");
            if (peak > 64) throw new AssertionError("Continuation exceeded existing disk lease budget");
            for (int i = 0; i < 1000; i++) {
                if (completion.get().execute(decoded::incrementAndGet))
                    throw new AssertionError("More than one queued continuation claimed a single lease");
            }
            release.countDown();
            await(delivered, "Completed NBT never reached requester");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (runtime.pendingReads() != 0 && System.nanoTime() < deadline) Thread.yield();
            if (decoded.get() != 1 || rejected.get() != 0 || runtime.pendingReads() != 0)
                throw new AssertionError("Continuation leaked, duplicated, or rejected a column");
            System.out.println("PASS one exact NBT decode delivered; all 64 leases released");
        } finally {
            release.countDown();
            runtime.shutdown();
        }
    }

    private static void listeners() throws Exception {
        listeners(new IllegalStateException("Disconnected request callback"));
        listeners(new LinkageError("Incompatible mod callback"));
        listeners(new AssertionError("Broken callback invariant"));
    }

    private static void listeners(Throwable callbackFailure) throws Exception {
        DiskTaskRuntime runtime = new DiskTaskRuntime(1, 1, () -> 1, () -> true);
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger(), delivered = new AtomicInteger(), failed = new AtomicInteger();
        var key = new DiskTaskRuntime.ReadKey(ResourceLocation.parse("test:world"), 12, -3);
        try {
            runtime.submitCoalescedRead(key, false, 64, 0, () -> {
                reads.incrementAndGet(); started.countDown();
                try { await(release, "Shared read blocked"); } catch (InterruptedException error) { throw new RuntimeException(error); }
                return "unchanged-column";
            }, value -> {
                if (callbackFailure instanceof RuntimeException error) throw error;
                throw (Error) callbackFailure;
            }, error -> failed.incrementAndGet());
            await(started, "Shared read did not start");
            for (int i = 0; i < 3; i++) runtime.submitCoalescedRead(key, false, 64, 0,
                    () -> { throw new AssertionError("Coalesced read duplicated IO"); },
                    value -> { if (!"unchanged-column".equals(value)) throw new AssertionError("Wrong shared data"); delivered.incrementAndGet(); },
                    error -> failed.incrementAndGet());
            release.countDown(); drained(runtime);
            if (reads.get() != 1 || delivered.get() != 3 || failed.get() != 1)
                throw new AssertionError("One failed request lost healthy shared listeners: reads=" + reads + ", delivered=" + delivered + ", failed=" + failed);
            System.out.println("PASS one physical read, three healthy requesters delivered despite one " + callbackFailure.getClass().getSimpleName());
        } finally { release.countDown(); runtime.shutdown(); }
    }

    private static void cancelled() throws Exception {
        DiskTaskRuntime runtime = new DiskTaskRuntime(1, 1, () -> 1, () -> true);
        CountDownLatch started = new CountDownLatch(1), blocked = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<DiskTaskRuntime.AsyncReadCompletion<String>> completion = new AtomicReference<>();
        AtomicInteger decoded = new AtomicInteger(), rejected = new AtomicInteger();
        try {
            runtime.<String>submitCoalescedNbtRead(new DiskTaskRuntime.ReadKey(ResourceLocation.parse("test:world"), 20, 20),
                    1, 64, 64, () -> true, value -> { completion.set(value); started.countDown(); },
                    value -> { throw new AssertionError("Cancelled read delivered"); }, error -> rejected.incrementAndGet());
            await(started, "NBT did not start");
            runtime.submitRead(64, () -> {
                blocked.countDown();
                try { await(release, "Blocker stuck"); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }, error -> { throw new AssertionError(error); });
            await(blocked, "Read worker not blocked");
            if (!completion.get().execute(decoded::incrementAndGet)) throw new AssertionError("Continuation rejected");
            completion.get().fail(new IllegalStateException("Request cancelled after NBT returned"));
            if (runtime.pendingReads() != 2)
                throw new AssertionError("Cancelled queued continuation released its lease early: " + runtime.pendingReads());
            release.countDown(); drained(runtime);
            if (decoded.get() != 0 || rejected.get() != 1) throw new AssertionError("Cancelled read ran or notified twice");
            System.out.println("PASS cancelled queued decode holds its lease until discarded/executed, skips terrain work, releases once");
        } finally { release.countDown(); runtime.shutdown(); }
    }

    private static void shutdown() throws Exception {
        DiskTaskRuntime runtime = new DiskTaskRuntime(1, 1, () -> 1, () -> true);
        CountDownLatch started = new CountDownLatch(1), blocked = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<DiskTaskRuntime.AsyncReadCompletion<String>> old = new AtomicReference<>();
        AtomicInteger rejected = new AtomicInteger();
        try {
            runtime.<String>submitCoalescedNbtRead(new DiskTaskRuntime.ReadKey(ResourceLocation.parse("test:world"), 5, 5),
                    1, 64, 64, () -> true, value -> { old.set(value); started.countDown(); },
                    value -> { throw new AssertionError("Stopped read delivered"); }, error -> rejected.incrementAndGet());
            await(started, "NBT did not start");
            runtime.submitRead(64, () -> {
                blocked.countDown();
                try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }, error -> {});
            await(blocked, "Read worker not blocked");
            old.get().execute(() -> { throw new AssertionError("Shutdown executed queued decode"); });
            for (int i = 0; i < 10; i++) runtime.submitRead(64, () -> {}, error -> {});
            runtime.shutdown(); drained(runtime);
            if (old.get().execute(() -> {}) || old.get().complete("late") || rejected.get() != 1)
                throw new AssertionError("Stopped completion was reused or rejected twice");
            runtime.restart();
            CountDownLatch next = new CountDownLatch(1);
            AtomicReference<DiskTaskRuntime.AsyncReadCompletion<String>> current = new AtomicReference<>();
            runtime.<String>submitCoalescedNbtRead(new DiskTaskRuntime.ReadKey(ResourceLocation.parse("test:world"), 5, 5),
                    1, 64, 64, () -> true, value -> { current.set(value); next.countDown(); }, value -> {}, error -> {});
            await(next, "Restarted NBT did not start");
            old.get().fail(new IllegalStateException("Late IO failure from old session"));
            if (runtime.pendingReads() != 1 || runtime.snapshot().nbtReadsActive() != 1)
                throw new AssertionError("Old completion changed a new session's accounting");
            current.get().complete("new"); drained(runtime);
            System.out.println("PASS shutdown clears queued tasks and async leases; late callbacks cannot alter restarted reads");
        } finally { release.countDown(); runtime.shutdown(); }
    }

    private static void racing() throws Exception {
        DiskTaskRuntime runtime = new DiskTaskRuntime(1, 2, () -> 2, () -> true);
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (int iteration = 0; iteration < 250; iteration++) {
                CountDownLatch started = new CountDownLatch(1), go = new CountDownLatch(1);
                AtomicReference<DiskTaskRuntime.AsyncReadCompletion<String>> completion = new AtomicReference<>();
                AtomicInteger terminal = new AtomicInteger(), decoded = new AtomicInteger();
                runtime.<String>submitCoalescedNbtRead(new DiskTaskRuntime.ReadKey(ResourceLocation.parse("test:world"), iteration, 30),
                        2, 64, 64, () -> true, value -> { completion.set(value); started.countDown(); },
                        value -> terminal.incrementAndGet(), error -> terminal.incrementAndGet());
                await(started, "Racing NBT did not start");
                var scheduling = workers.submit(() -> {
                    try { await(go, "Race not released"); } catch (InterruptedException error) { throw new RuntimeException(error); }
                    completion.get().execute(() -> { decoded.incrementAndGet(); completion.get().complete("exact"); });
                });
                var cancelling = workers.submit(() -> {
                    try { await(go, "Race not released"); } catch (InterruptedException error) { throw new RuntimeException(error); }
                    completion.get().fail(new IllegalStateException("Concurrent read cancellation"));
                });
                go.countDown(); scheduling.get(3, TimeUnit.SECONDS); cancelling.get(3, TimeUnit.SECONDS);
                drained(runtime);
                if (terminal.get() != 1 || decoded.get() > 1 || runtime.snapshot().nbtReadsActive() != 0)
                    throw new AssertionError("Concurrent scheduling/cancellation duplicated a result or leaked gate capacity");
            }
            System.out.println("PASS 250 concurrent continuation/cancellation races: one outcome, no leaked read or NBT slots");
        } finally { workers.shutdownNow(); runtime.shutdown(); }
    }

    private static void queuedShutdown() throws Exception {
        DiskTaskRuntime runtime = new DiskTaskRuntime(1, 1, () -> 1, () -> true);
        CountDownLatch blocked = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger rejected = new AtomicInteger();
        try {
            runtime.submitRead(64, () -> {
                blocked.countDown();
                try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }, error -> {});
            await(blocked, "Read worker not blocked");
            // Explicitly exercise the original queue setting when an administrator disables resource caps.
            for (int i = 0; i < 2048; i++) runtime.<String>submitCoalescedNbtRead(
                    new DiskTaskRuntime.ReadKey(ResourceLocation.parse("test:world"), i, 700),
                    1, 2048, 64, () -> true, pending -> { throw new AssertionError("Stopped NBT began IO"); },
                    value -> { throw new AssertionError("Stopped NBT delivered"); }, error -> rejected.incrementAndGet());
            runtime.shutdown(); drained(runtime);
            if (rejected.get() != 2048) throw new AssertionError("Shutdown lost queued NBT request failures: " + rejected);
            System.out.println("PASS 2,048 queued NBT reads shut down iteratively without recursion or leaked requests");
        } finally { release.countDown(); runtime.shutdown(); }
    }

    private static void resetRunning() throws Exception {
        DiskTaskRuntime runtime = new DiskTaskRuntime(1, 1, () -> 1, () -> true);
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            runtime.submitRead(64, () -> {
                started.countDown();
                while (release.getCount() != 0) {
                    try { release.await(); } catch (InterruptedException ignored) { }
                }
            }, error -> {});
            await(started, "Old IO did not start");
            var field = DiskTaskRuntime.class.getDeclaredField("readExecutor"); field.setAccessible(true);
            var oldExecutor = (java.util.concurrent.ThreadPoolExecutor) field.get(runtime);
            runtime.shutdown(); runtime.resetPendingCounts(); runtime.restart();
            CountDownLatch next = new CountDownLatch(1);
            AtomicReference<DiskTaskRuntime.AsyncReadCompletion<String>> current = new AtomicReference<>();
            runtime.<String>submitCoalescedNbtRead(new DiskTaskRuntime.ReadKey(ResourceLocation.parse("test:world"), 80, 80),
                    1, 64, 64, () -> true, value -> { current.set(value); next.countDown(); }, value -> {}, error -> {});
            await(next, "Restarted read did not start");
            int bothActive = runtime.pendingReads();
            release.countDown();
            if (!oldExecutor.awaitTermination(3, TimeUnit.SECONDS)) throw new AssertionError("Old IO did not finish");
            int newActive = runtime.pendingReads();
            if (bothActive != 2 || newActive != 1)
                throw new AssertionError("Counter reset forgot running IO or old finally stole new lease: before=" + bothActive + ", after=" + newActive);
            current.get().complete("exact"); drained(runtime);
            System.out.println("PASS reset/restart retains live IO leases; an old interrupted worker cannot subtract a new request");
        } finally { release.countDown(); runtime.shutdown(); }
    }

    private static void decodeError() throws Exception {
        for (Error injected : new Error[]{new LinkageError("Decode compatibility error"), new AssertionError("Decode invariant"),
                new VirtualMachineError("Synthetic fatal-error cleanup probe") { }}) {
            DiskTaskRuntime runtime = new DiskTaskRuntime(1, 1, () -> 1, () -> true);
            CountDownLatch started = new CountDownLatch(1), rejected = new CountDownLatch(1), escaped = new CountDownLatch(1);
            AtomicReference<DiskTaskRuntime.AsyncReadCompletion<String>> completion = new AtomicReference<>();
            try {
                runtime.<String>submitCoalescedNbtRead(new DiskTaskRuntime.ReadKey(ResourceLocation.parse("test:world"), 90, 90),
                        1, 64, 64, () -> true, value -> { completion.set(value); started.countDown(); },
                        value -> { throw new AssertionError("Failed decode delivered terrain"); }, error -> rejected.countDown());
                await(started, "Error probe NBT did not start");
                completion.get().execute(() -> {
                    Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> {
                        if (error == injected) escaped.countDown();
                    });
                    throw injected;
                });
                await(rejected, "Decode error never notified requester"); drained(runtime);
                if (injected instanceof VirtualMachineError) await(escaped, "Fatal error was swallowed");
                else if (escaped.getCount() != 1) throw new AssertionError("Recoverable decode error escaped isolation");
            } finally { runtime.shutdown(); }
        }
        System.out.println("PASS decode LinkageError/AssertionError release leases; fatal VM errors release leases and remain observable");
    }
}
