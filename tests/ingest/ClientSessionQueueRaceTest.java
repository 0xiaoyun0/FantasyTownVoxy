package dev.xantha.vss.networking.client;

import dev.xantha.vss.networking.payloads.VoxelColumnS2CPayload;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.Level;

/** Block the actual old decoder after its epoch guard, then publish a new-session full column. */
public final class ClientSessionQueueRaceTest {
    static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static ClientLevel level(net.minecraft.resources.ResourceKey<Level> dimension) throws Exception {
        // No world constructor/game process. Empty valid columns need no registry entries.
        RegistryAccess access = (RegistryAccess) Proxy.newProxyInstance(RegistryAccess.class.getClassLoader(),
                new Class<?>[]{RegistryAccess.class}, (proxy, method, args) -> {
                    if (method.getName().equals("registryOrThrow")) return null;
                    throw new AssertionError("Unexpected registry use: " + method);
                });
        return new ClientLevel(access, dimension);
    }

    private static void blockedOnBackpressure(Thread worker) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < until) {
            if (worker.getState() == Thread.State.BLOCKED) {
                for (var frame : worker.getStackTrace()) {
                    if (frame.getClassName().equals(ClientLodBackpressure.class.getName())
                            && frame.getMethodName().equals("pauseDrain")) return;
                }
            }
            Thread.sleep(1);
        }
        throw new AssertionError("Old real decoder never blocked in pauseDrain after its epoch check; state=" + worker.getState());
    }

    public static void main(String[] args) throws Exception {
        int failures = 0;
        for (boolean sameDimension : new boolean[]{true, false}) {
            try { race(sameDimension); }
            catch (Throwable error) { failures++; System.err.println("FAIL " + (sameDimension ? "same-dimension reconnect" : "dimension switch") + ": " + error); }
        }
        if (failures != 0) throw new AssertionError(failures + " real session queue races failed");
    }

    private static void race(boolean sameDimension) throws Exception {
        VSSClientNetworking.dispatched.set(0); VSSClientNetworking.failed.set(0);
        var processor = new ClientColumnProcessor(); processor.beginSession();
        ClientLevel level = level(Level.OVERWORLD);
        var newDimension = sameDimension ? Level.OVERWORLD : Level.NETHER;
        var epoch = (AtomicInteger) field(ClientColumnProcessor.class, "sessionEpoch").get(processor);
        var processing = (AtomicBoolean) field(ClientColumnProcessor.class, "processing").get(processor);
        // Invoke the real scheduled worker wrapper, including its completion/finally ownership.
        Method work = Arrays.stream(ClientColumnProcessor.class.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("lambda$scheduleProcessing$")
                        && Arrays.equals(method.getParameterTypes(), new Class<?>[]{ClientLevel.class, int.class}))
                .findFirst().orElseThrow();
        work.setAccessible(true);
        int oldEpoch = epoch.get();
        check(processor.offer(new VoxelColumnS2CPayload(3, 0, 0, Level.OVERWORLD, 10L,
                new byte[]{0}, true, 333L, 0, 1, new int[0]), false, false), "Old session queue fixture failed");
        processing.set(true);
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        Thread old = new Thread(() -> {
            try { work.invoke(processor, level, oldEpoch); }
            catch (InvocationTargetException error) { workerFailure.set(error.getCause()); }
            catch (Throwable error) { workerFailure.set(error); }
        }, "old-session-decoder-probe");
        old.setDaemon(true);
        try {
            long expectedBytes;
            synchronized (ClientLodBackpressure.class) {
                old.start();
                blockedOnBackpressure(old);
                if (sameDimension) processor.beginSession();
                else processor.clearPendingTransfers();
                // One-part empty columns are a valid exact transfer (e.g. empty terrain).
                var payload = new VoxelColumnS2CPayload(7, 2, 3, newDimension, 100L,
                        new byte[]{0}, true, 777L, 0, 1, new int[0]);
                check(processor.offer(payload, !sameDimension, false), "New session did not accept its complete column");
                check(processor.getQueuedCount() == 1, "Fixture did not queue exactly one new-session column");
                expectedBytes = processor.getQueuedBytes();
                processing.set(true); // A current-session worker has been reserved behind the old worker.
            }
            old.join(3000);
            check(!old.isAlive(), "Old decoder deadlocked across session reset");
            check(workerFailure.get() == null, "Old decoder failed outside queue race: " + workerFailure.get());
            int remaining = processor.getQueuedCount();
            long remainingBytes = processor.getQueuedBytes();
            System.out.println((sameDimension ? "sameDimension" : "crossDimension") + ": oldEpoch=" + oldEpoch + ", currentEpoch=" + epoch.get() + ", retainedColumns=" + remaining
                    + ", retainedBytes=" + remainingBytes + "/" + expectedBytes + ", dispatches=" + VSSClientNetworking.dispatched
                    + ", failed=" + VSSClientNetworking.failed + ", currentWorkerReserved=" + processing.get());
            check(remaining == 1 && remainingBytes == expectedBytes && processing.get(),
                    "Old worker swallowed a new-session complete LOD column/accounting or released the new worker's ownership");
            check(VSSClientNetworking.dispatched.get() == 0 && VSSClientNetworking.failed.get() == 0,
                    "Old worker dispatched or rejected a different session's column");
            work.invoke(processor, level(newDimension), epoch.get());
            check(processor.getQueuedCount() == 0 && processor.getQueuedBytes() == 0
                            && VSSClientNetworking.dispatched.get() == 1 && VSSClientNetworking.failed.get() == 0 && !processing.get(),
                    "Current-session decoder did not dispatch the retained column exactly once");
            System.out.println("PASS real decoder/session race: old worker retains new column and exact accounting; current worker dispatches once");
        } finally { processor.shutdown(); }
    }
}
