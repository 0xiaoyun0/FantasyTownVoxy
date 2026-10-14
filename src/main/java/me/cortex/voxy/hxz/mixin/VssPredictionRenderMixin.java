package me.cortex.voxy.hxz.mixin;
import me.cortex.voxy.hxz.client.HxzClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="dev.xantha.vss.client.prediction.PredictionRenderer", remap=false)
public class VssPredictionRenderMixin {
    @Inject(method="predictionHorizonBlocks()I",at=@At("RETURN"),cancellable=true)
    private static void hxz$range(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(HxzClient.capRange(cir.getReturnValue(),1,1024));
    }
}
