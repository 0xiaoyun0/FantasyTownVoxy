package me.cortex.voxy.hxz.mixin;
import me.cortex.voxy.hxz.client.HxzClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="dev.xantha.vss.client.prediction.PredictionTileManager", remap=false)
public class VssPredictionLayoutMixin {
    @Inject(method="strictDisplaySampling",at=@At("HEAD"),cancellable=true)
    private static void hxz$strict(CallbackInfoReturnable<Boolean> ci){
        if(me.cortex.voxy.hxz.HxzSettings.CONFIG.predictionStrictSampling)ci.setReturnValue(true);
    }
    @ModifyArg(method="createLayout",at=@At(value="INVOKE",target="Ldev/xantha/vss/client/prediction/VssLodLayout;of(IDZZ)Ldev/xantha/vss/client/prediction/VssLodLayout;"),index=0)
    private int hxz$range(int requested) { return HxzClient.capRange(requested,1,1024); }
}
