package me.cortex.voxy.hxz.mixin;

import me.cortex.voxy.hxz.HxzSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** Cover the native batch path used by the renderer, as well as Java's point path. */
@Mixin(targets="dev.xantha.vss.client.prediction.RustTerrainSampler",remap=false)
public class StableNativePredictionMixin {
    @ModifyVariable(method="sampleGrid(IIIII[Ldev/xantha/vss/client/prediction/ClientColumnSample;IIZZ)[Ldev/xantha/vss/client/prediction/ClientColumnSample;",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private boolean hxz$preview(boolean value){return !HxzSettings.CONFIG.stablePrediction&&value;}
    @ModifyVariable(method="sampleGrid(IIIII[Ldev/xantha/vss/client/prediction/ClientColumnSample;IIZZ)[Ldev/xantha/vss/client/prediction/ClientColumnSample;",at=@At("HEAD"),argsOnly=true,ordinal=1)
    private boolean hxz$display(boolean value){return !HxzSettings.CONFIG.predictionStrictSampling&&value;}
}
