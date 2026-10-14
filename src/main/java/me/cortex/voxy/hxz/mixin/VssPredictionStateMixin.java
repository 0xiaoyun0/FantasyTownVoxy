package me.cortex.voxy.hxz.mixin;
import me.cortex.voxy.hxz.client.HxzClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="dev.xantha.vss.client.prediction.ClientPredictionState", remap=false)
public class VssPredictionStateMixin {
    @Inject(method="predictionHorizonBlocks()D",at=@At("RETURN"),cancellable=true)
    private static void hxz$range(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue((double)HxzClient.capRange(cir.getReturnValue().intValue(),1,1024));
    }
}
