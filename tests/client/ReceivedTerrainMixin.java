package hxz.smoke.mixin;

import dev.xantha.vss.networking.client.VSSClientNetworking;
import dev.xantha.vss.networking.payloads.VoxelColumnS2CPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records data arriving from the test server, beyond the vanilla view distance. */
@Mixin(value=VSSClientNetworking.class,remap=false)
public class ReceivedTerrainMixin {
    @Inject(method="handleVoxelColumn",at=@At("RETURN"))
    private static void hxz$received(VoxelColumnS2CPayload payload, CallbackInfo ci) {
        if(VSSClientNetworking.isClientLodSessionActive() && payload.completeColumn()
                && payload.chunkX()>=0 && payload.chunkX()<4
                && payload.chunkZ()>=8 && payload.chunkZ()<12)
            hxz.smoke.HxzSmokeProbe.farColumns++;
    }
}
