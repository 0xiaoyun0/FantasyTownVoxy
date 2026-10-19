package hxz.smoke;

import java.util.*;

/** Diagnostic-only bounded observation: every requested column still executes unchanged. */
public final class ExteriorCostTrace {
    private static final int MAX_KEYS = 32_768;
    private static final Map<Object, State> SAMPLERS = new WeakHashMap<>();
    private static final ThreadLocal<Call> CURRENT = new ThreadLocal<>();
    private static int retainedKeys;
    private static long completed, repeated, nulls, repeatedNulls, nanos, repeatedNanos, changed, dropped, aborted;

    private record Key(int x, int z, int step, int bottom, int top) { }
    private record Digest(boolean missing, int length, int hash) { }
    private static final class State { final Map<Key, Digest> seen = new HashMap<>(); }
    private record Call(State state, Key key, long start) { }

    public static void enter(Object sampler, int x, int z, int step, int bottom, int top) {
        State state;
        synchronized (SAMPLERS) {
            state = SAMPLERS.computeIfAbsent(sampler, ignored -> new State());
            if (CURRENT.get() != null) aborted++;
        }
        CURRENT.set(new Call(state, new Key(x, z, step, bottom, top), System.nanoTime()));
    }

    public static void returned(boolean[] result) {
        Call call = CURRENT.get();
        CURRENT.remove();
        if (call == null) return;
        long elapsed = System.nanoTime() - call.start();
        Digest digest = new Digest(result == null, result == null ? 0 : result.length, Arrays.hashCode(result));
        synchronized (SAMPLERS) {
            completed++; nanos += elapsed;
            if (result == null) nulls++;
            Digest previous = call.state().seen.get(call.key());
            if (previous != null) {
                repeated++; repeatedNanos += elapsed;
                if (result == null) repeatedNulls++;
                if (!previous.equals(digest)) changed++;
            } else if (retainedKeys < MAX_KEYS) {
                call.state().seen.put(call.key(), digest); retainedKeys++;
            } else dropped++;
        }
    }

    public static String summary() {
        synchronized (SAMPLERS) {
            return "HXZ_EXTERIOR_AUDIT calls=" + completed + " exactRepeats=" + repeated
                    + " nullResults=" + nulls + " repeatedNulls=" + repeatedNulls
                    + " totalMs=" + nanos / 1_000_000L + " exactRepeatMs=" + repeatedNanos / 1_000_000L
                    + " changedDigests=" + changed + " retainedKeys=" + retainedKeys
                    + " maxKeys=" + MAX_KEYS + " untrackedNewKeys=" + dropped
                    + " observedAborts=" + aborted + " samplerInstances=" + SAMPLERS.size();
        }
    }
}
