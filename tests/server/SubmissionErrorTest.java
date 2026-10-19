package dev.xantha.vss.networking.server.runtime;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;

/** Real executor recreation/thread-start failures must terminate the existing NBT lease. */
public final class SubmissionErrorTest {
    private static final class FatalProbe extends VirtualMachineError {
        FatalProbe(String message) { super(message); }
    }

    @SuppressWarnings("removal")
    public static void main(String[] args) throws Exception {
        int failures = 0;
        for (String scenario : new String[]{"supplier", "threadFactory", "starterFatal", "starterThreadDeath"}) {
            try {
                switch (scenario) {
                    case "supplier" -> submission(true);
                    case "threadFactory" -> submission(false);
                    case "starterFatal" -> starter(new FatalProbe("Synthetic starter fatal error"));
                    case "starterThreadDeath" -> starter(new ThreadDeath());
                }
            } catch (Throwable error) {
                failures++;
                System.err.println("FAIL " + scenario + ": " + error);
            }
        }
        if (failures != 0) throw new AssertionError(failures + " submission/starter error scenarios failed");
    }

    private static void await(CountDownLatch latch, String message) throws Exception {
        if (!latch.await(3, TimeUnit.SECONDS)) throw new AssertionError(message);
    }

    private static ThreadPoolExecutor executor(DiskTaskRuntime runtime) throws Exception {
        var field = DiskTaskRuntime.class.getDeclaredField("readExecutor");
        field.setAccessible(true);
        return (ThreadPoolExecutor) field.get(runtime);
    }

    private static void clean(DiskTaskRuntime runtime) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while ((runtime.pendingReads() != 0 || runtime.snapshot().nbtReadsActive() != 0)
                && System.nanoTime() < until) Thread.sleep(1);
        var inFlight = DiskTaskRuntime.class.getDeclaredField("inFlightNbtReads"); inFlight.setAccessible(true);
        var active = DiskTaskRuntime.class.getDeclaredField("activeNbtCompletions"); active.setAccessible(true);
        int pending = runtime.pendingReads(), gate = runtime.snapshot().nbtReadsActive();
        int shared = ((java.util.Map<?, ?>) inFlight.get(runtime)).size();
        int completions = ((java.util.Set<?>) active.get(runtime)).size();
        if (pending != 0 || gate != 0 || shared != 0 || completions != 0)
            throw new AssertionError("Leaked NBT state: pending=" + pending + ", gate=" + gate
                    + ", shared=" + shared + ", completions=" + completions);
    }

    private static void submission(boolean supplierFailure) throws Exception {
        FatalProbe injected = new FatalProbe("Synthetic executor " + (supplierFailure ? "supplier" : "thread factory") + " failure");
        AtomicBoolean failSupplier = new AtomicBoolean();
        DiskTaskRuntime runtime = new DiskTaskRuntime(1, 1, () -> {
            if (failSupplier.get()) throw injected;
            return 1;
        }, () -> true);
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<DiskTaskRuntime.AsyncReadCompletion<String>> completion = new AtomicReference<>();
        AtomicInteger rejected = new AtomicInteger(), decoded = new AtomicInteger();
        try {
            runtime.<String>submitCoalescedNbtRead(new DiskTaskRuntime.ReadKey(ResourceLocation.parse("test:world"), 100, 100),
                    1, 64, 64, () -> true, value -> { completion.set(value); started.countDown(); },
                    value -> { throw new AssertionError("Failed submission delivered terrain"); }, error -> rejected.incrementAndGet());
            await(started, "NBT did not start");
            ThreadPoolExecutor executor = executor(runtime);
            if (supplierFailure) {
                // The async IO still owns its reservation after the old executor terminates.
                executor.shutdown();
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) throw new AssertionError("Old worker did not terminate");
                failSupplier.set(true);
            } else {
                // Use ThreadPoolExecutor's real addWorker -> ThreadFactory failure path.
                executor.setThreadFactory(task -> { throw injected; });
                executor.setKeepAliveTime(1, TimeUnit.MILLISECONDS);
                long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (executor.getPoolSize() != 0 && System.nanoTime() < until) Thread.sleep(1);
                if (executor.getPoolSize() != 0) throw new AssertionError("Idle worker did not expire");
            }
            Throwable escaped = null;
            try { completion.get().execute(decoded::incrementAndGet); }
            catch (Throwable error) { escaped = error; }
            if (escaped != injected) throw new AssertionError("Fatal submission error was swallowed or replaced: " + escaped);
            clean(runtime);
            if (!completion.get().isComplete() || rejected.get() != 1 || decoded.get() != 0)
                throw new AssertionError("Submission failure did not terminate once: rejected=" + rejected + ", decoded=" + decoded);
            if (completion.get().execute(decoded::incrementAndGet) || completion.get().complete("late"))
                throw new AssertionError("Failed submission accepted a late result");
            System.out.println("PASS " + (supplierFailure ? "executor supplier" : "real ThreadPoolExecutor thread factory")
                    + " fatal error escapes unchanged; lease, gate and shared state release exactly once");
        } finally { failSupplier.set(false); runtime.shutdown(); }
    }

    private static void starter(Error injected) throws Exception {
        DiskTaskRuntime runtime = new DiskTaskRuntime(1, 1, () -> 1, () -> true);
        CountDownLatch escaped = new CountDownLatch(1), rejected = new CountDownLatch(1);
        AtomicInteger outcomes = new AtomicInteger();
        try {
            runtime.<String>submitCoalescedNbtRead(new DiskTaskRuntime.ReadKey(ResourceLocation.parse("test:world"), 101, 101),
                    1, 64, 64, () -> true, value -> {
                        Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> {
                            if (error == injected) escaped.countDown();
                        });
                        throw injected;
                    }, value -> { throw new AssertionError("Fatal starter delivered terrain"); },
                    error -> { outcomes.incrementAndGet(); rejected.countDown(); });
            await(rejected, "Fatal starter never notified requester");
            clean(runtime);
            await(escaped, "Starter swallowed " + injected.getClass().getSimpleName());
            if (outcomes.get() != 1) throw new AssertionError("Starter notified more than once");
            System.out.println("PASS starter " + injected.getClass().getSimpleName() + " is observable after lease and gate cleanup");
        } finally { runtime.shutdown(); }
    }
}
