package dev.xantha.vss.networking.client;

import dev.xantha.vss.api.VoxelColumnData;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Isolate the game/network endpoint; the processor, assembler and epoch logic remain real. */
public final class VSSClientNetworking {
    public static final AtomicInteger dispatched = new AtomicInteger();
    public static final AtomicInteger failed = new AtomicInteger();
    public static boolean isClientLodSessionActive() { return true; }
    static void processAssembledColumn(ClientLevel level, ClientColumnTransferAssembler.AssembledColumn column,
            VoxelColumnData data) { dispatched.incrementAndGet(); }
    public static void onColumnTransferFailed(int id, long transfer, ResourceKey<Level> dimension, int x, int z) {
        failed.incrementAndGet();
    }
}
