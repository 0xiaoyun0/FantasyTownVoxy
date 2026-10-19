package dev.xantha.vss.networking.server.runtime;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import net.minecraft.resources.ResourceLocation;
import me.cortex.voxy.hxz.server.ServerSafetyPolicy;

/** Hot configuration changes are supported while existing NBT and disk queues remain active. */
public final class GateRejectionTest {
    public static void main(String[] args) throws Exception {
        var runtime = new DiskTaskRuntime(1, 1, () -> 1, () -> true);
        var oldLimits = ServerSafetyPolicy.limits;
        var started = new CountDownLatch(1);
        var blocked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var active = new AtomicReference<DiskTaskRuntime.AsyncReadCompletion<String>>();
        var rejected = new AtomicInteger();
        try {
            runtime.<String>submitCoalescedNbtRead(key(-1), 1, 2048, 64, () -> true,
                    pending -> { active.set(pending); started.countDown(); }, value -> {}, error -> rejected.incrementAndGet());
            if (!started.await(3, TimeUnit.SECONDS)) throw new AssertionError("NBT did not start");
            runtime.submitRead(64, () -> {
                blocked.countDown();
                try { release.await(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }, error -> {});
            if (!blocked.await(3, TimeUnit.SECONDS)) throw new AssertionError("Worker did not block");
            for (int i = 0; i < 62; i++) runtime.submitRead(64, () -> {}, error -> { throw new AssertionError("Capacity fixture"); });
            for (int i = 0; i < 2048; i++) runtime.<String>submitCoalescedNbtRead(key(i), 1, 2048, 64, () -> true,
                    pending -> { throw new AssertionError("Over-limit queued NBT began IO"); }, value -> {}, error -> rejected.incrementAndGet());
            var parts = oldLimits.getClass().getRecordComponents();
            Object[] values = new Object[parts.length];
            Class<?>[] types = new Class<?>[parts.length];
            for (int i = 0; i < parts.length; i++) {
                types[i] = parts[i].getType(); values[i] = parts[i].getAccessor().invoke(oldLimits);
                if (parts[i].getName().equals("diskTasks")) values[i] = 16;
            }
            ServerSafetyPolicy.limits = (ServerSafetyPolicy.Limits) oldLimits.getClass().getDeclaredConstructor(types).newInstance(values);
            active.get().complete("exact");
            if (rejected.get() != 2048 || runtime.snapshot().nbtReadsActive() != 0 || runtime.snapshot().nbtReadsQueued() != 0)
                throw new AssertionError("Hot limit decrease lost failures or gate leases: rejected=" + rejected + ", state=" + runtime.snapshot());
            var map = DiskTaskRuntime.class.getDeclaredField("inFlightNbtReads"); map.setAccessible(true);
            if (!((java.util.Map<?, ?>) map.get(runtime)).isEmpty()) throw new AssertionError("Rejected shared reads remain in memory");
            System.out.println("PASS hot disk limit 64 -> 16: 2,048 queued NBT reads rejected iteratively, no leaked requests or gate slots");
        } finally { ServerSafetyPolicy.limits = oldLimits; release.countDown(); runtime.shutdown(); }
    }

    private static DiskTaskRuntime.ReadKey key(int x) {
        return new DiskTaskRuntime.ReadKey(ResourceLocation.parse("test:world"), x, 800);
    }
}
