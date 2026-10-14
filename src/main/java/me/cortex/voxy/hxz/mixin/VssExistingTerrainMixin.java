package me.cortex.voxy.hxz.mixin;

import dev.xantha.vss.networking.payloads.SessionConfigS2CPayload;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A cold LOD cache must not hide already-generated terrain when worldgen is disabled. */
@Pseudo
@Mixin(targets="dev.xantha.vss.networking.client.LodRequestManager", remap=false)
public class VssExistingTerrainMixin {
    @Shadow private SessionConfigS2CPayload sessionConfig;

    @Inject(method="requiresFirstPassCacheProbe(J)Z",at=@At("HEAD"),cancellable=true)
    private void hxz$readExistingTerrain(long position, CallbackInfoReturnable<Boolean> cir) {
        if(me.cortex.voxy.hxz.HxzSettings.CONFIG.existingTerrainFix && sessionConfig!=null && !sessionConfig.generationEnabled()) {
            // Ordinary sync requests consult loaded chunks and bounded NBT reads.
            // Generation stays disabled and normal sync rate limits still apply.
            cir.setReturnValue(false);
        }
    }

    @ModifyArg(method="createRequestWindow",at=@At(value="INVOKE",
            target="Ldev/xantha/vss/networking/client/RequestWindow;<init>(IIIIIII)V"),index=5)
    private int hxz$probeCapacity(int requested) {
        // Unused probe slots would make the scan advance past unsent columns
        // after the ordinary sync bucket is exhausted.
        return me.cortex.voxy.hxz.HxzSettings.CONFIG.existingTerrainFix && sessionConfig!=null && !sessionConfig.generationEnabled() ? 0 : requested;
    }
}
