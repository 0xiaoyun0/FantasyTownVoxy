package dev.xantha.vss.networking.server.runtime;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs the real VSS executor, holding disk work to reproduce a saturated drive. */
public final class ExecutorOverloadTest {
    public static void main(String[] args) {
        var queued = new ArrayDeque<Runnable>();
        var pending = new AtomicInteger();
        var rejected = new AtomicInteger();
        var executor = new TrackedTaskExecutor(() -> queued::add, pending);
        int accepted = 0;
        for (int i = 0; i < 5000; i++)
            if (executor.submitUnrestricted(() -> {}, error -> rejected.incrementAndGet())) accepted++;
        boolean bounded = accepted <= 64 && queued.size() <= 64 && pending.get() <= 64;
        System.out.println((bounded ? "PASS" : "FAIL") + " stalled disk: accepted=" + accepted
            + ", queued=" + queued.size() + ", rejected=" + rejected.get());
        while (!queued.isEmpty()) queued.remove().run();
        if (pending.get() != 0) throw new AssertionError("Completed work leaked reservations");

        var failedPending = new AtomicInteger();
        var broken = new TrackedTaskExecutor(() -> { throw new IllegalStateException("disk executor unavailable"); }, failedPending);
        try { broken.submit(2, () -> {}, error -> {}); } catch (IllegalStateException expected) { }
        boolean released = failedPending.get() == 0;
        System.out.println((released ? "PASS" : "FAIL") + " executor creation failure: pending=" + failedPending.get());
        try { broken.submitManual(2, task -> {}, error -> {}); } catch (IllegalStateException expected) { }
        released &= failedPending.get()==0;
        // Continuations must not bypass admission while a disk worker is stalled.
        int continuations=0;
        for(int i=0;i<5000;i++)if(executor.executeContinuation(()->{},error->{},0))continuations++;
        boolean continuationsBounded=continuations<=64;
        System.out.println((continuationsBounded?"PASS":"FAIL")+" stalled continuations: accepted="+continuations);
        while(!queued.isEmpty())queued.remove().run();
        bounded &= continuationsBounded && pending.get()==0;
        if (!bounded || !released) throw new AssertionError("Server work admission is not bounded/recoverable");
    }
}
