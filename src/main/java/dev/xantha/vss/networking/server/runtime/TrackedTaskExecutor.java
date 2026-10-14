/*
 * Decompiled with CFR 0.152.
 */
package dev.xantha.vss.networking.server.runtime;

import dev.xantha.vss.networking.server.runtime.DiskTaskRuntime;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TrackedTaskExecutor {
    private final Supplier<? extends Executor> executorSupplier;
    private final AtomicInteger pendingTasks;
    private final AtomicLong sequence = new AtomicLong();

    public TrackedTaskExecutor(Supplier<? extends Executor> executorSupplier, AtomicInteger pendingTasks) {
        this.executorSupplier = Objects.requireNonNull(executorSupplier, "executorSupplier");
        this.pendingTasks = Objects.requireNonNull(pendingTasks, "pendingTasks");
    }

    PendingTask beginTask() {
        this.pendingTasks.incrementAndGet();
        return new PendingTask(this.pendingTasks);
    }

    PendingTask tryBeginTask(int limit) {
        int current;
        int safeLimit = Math.max(0, me.cortex.voxy.hxz.server.ServerSafetyPolicy.diskLimit(limit));
        do {
            if ((current = this.pendingTasks.get()) < safeLimit) continue;
            return null;
        } while (!this.pendingTasks.compareAndSet(current, current + 1));
        return new PendingTask(this.pendingTasks);
    }

    boolean submit(int limit, Runnable task, Consumer<RejectedExecutionException> onRejected) {
        return this.submit(limit, task, onRejected, 1);
    }

    boolean submit(int limit, Runnable task, Consumer<RejectedExecutionException> onRejected, int priority) {
        Objects.requireNonNull(task, "task");
        PendingTask pendingTask = this.tryBeginTask(limit);
        if (pendingTask == null) {
            TrackedTaskExecutor.reject(onRejected, "VSS disk task queue is full");
            return false;
        }
        return this.submitTracked(pendingTask, task, onRejected, priority);
    }

    boolean submitUnrestricted(Runnable task, Consumer<RejectedExecutionException> onRejected) {
        Objects.requireNonNull(task, "task");
        if (me.cortex.voxy.hxz.server.ServerSafetyPolicy.boundedResources()) {
            return this.submit(me.cortex.voxy.hxz.server.ServerSafetyPolicy.limits.diskTasks(), task, onRejected, 0);
        }
        return this.submitTracked(this.beginTask(), task, onRejected, 0);
    }

    private boolean submitTracked(PendingTask pendingTask, Runnable task, Consumer<RejectedExecutionException> onRejected, int priority) {
        try {
            this.executorSupplier.get().execute(new PrioritizedTask(() -> {
                try {
                    task.run();
                }
                finally {
                    pendingTask.complete();
                }
            }, priority, this.sequence.incrementAndGet()));
            return true;
        }
        catch (RejectedExecutionException e) {
            pendingTask.complete();
            if (onRejected != null) {
                onRejected.accept(e);
            }
            return false;
        }
        catch (RuntimeException | Error e) {
            pendingTask.complete();
            throw e;
        }
    }

    boolean submitManual(int limit, Consumer<DiskTaskRuntime.PendingDiskTask> task, Consumer<RejectedExecutionException> onRejected) {
        return this.submitManual(limit, task, onRejected, 0);
    }

    boolean executeContinuation(Runnable task, Consumer<RejectedExecutionException> onRejected, int priority) {
        Objects.requireNonNull(task, "task");
        if (me.cortex.voxy.hxz.server.ServerSafetyPolicy.boundedResources()) {
            return this.submit(me.cortex.voxy.hxz.server.ServerSafetyPolicy.limits.diskTasks(),task,onRejected,priority);
        }
        try {
            this.executorSupplier.get().execute(new PrioritizedTask(task, priority, this.sequence.incrementAndGet()));
            return true;
        }
        catch (RejectedExecutionException e) {
            if (onRejected != null) {
                onRejected.accept(e);
            }
            return false;
        }
    }

    boolean submitManual(int limit, Consumer<DiskTaskRuntime.PendingDiskTask> task, Consumer<RejectedExecutionException> onRejected, int priority) {
        Objects.requireNonNull(task, "task");
        PendingTask pendingTask = this.tryBeginTask(limit);
        if (pendingTask == null) {
            TrackedTaskExecutor.reject(onRejected, "VSS disk task queue is full");
            return false;
        }
        try {
            this.executorSupplier.get().execute(new PrioritizedTask(() -> {
                try {
                    task.accept(pendingTask);
                }
                catch (RuntimeException e) {
                    pendingTask.complete();
                    throw e;
                }
                catch (Error e) {
                    pendingTask.complete();
                    throw e;
                }
            }, priority, this.sequence.incrementAndGet()));
            return true;
        }
        catch (RejectedExecutionException e) {
            pendingTask.complete();
            if (onRejected != null) {
                onRejected.accept(e);
            }
            return false;
        }
        catch (RuntimeException | Error e) {
            pendingTask.complete();
            throw e;
        }
    }

    public int pendingTasks() {
        return this.pendingTasks.get();
    }

    private static void reject(Consumer<RejectedExecutionException> onRejected, String message) {
        if (onRejected != null) {
            onRejected.accept(new RejectedExecutionException(message));
        }
    }

    static final class PendingTask
    implements DiskTaskRuntime.PendingDiskTask {
        private final AtomicInteger pendingTasks;
        private final AtomicBoolean complete = new AtomicBoolean();

        private PendingTask(AtomicInteger pendingTasks) {
            this.pendingTasks = pendingTasks;
        }

        @Override
        public void complete() {
            if (this.complete.compareAndSet(false, true)) {
                this.pendingTasks.updateAndGet(value -> Math.max(0, value - 1));
            }
        }

        @Override
        public boolean isComplete() {
            return this.complete.get();
        }
    }

    private record PrioritizedTask(Runnable delegate, int priority, long sequence) implements Runnable,
    Comparable<PrioritizedTask>
    {
        @Override
        public void run() {
            this.delegate.run();
        }

        @Override
        public int compareTo(PrioritizedTask other) {
            int priorityOrder = Integer.compare(this.priority, other.priority);
            return priorityOrder != 0 ? priorityOrder : Long.compare(this.sequence, other.sequence);
        }
    }
}

