package me.cortex.voxy.hxz.mixin;
import me.cortex.voxy.hxz.client.HxzClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="dev.xantha.vss.networking.client.LodRequestManager", remap=false)
public class VssNetworkRangeMixin {
    @Inject(method="getEffectiveLodDistance()I",at=@At("RETURN"),cancellable=true)
    private void hxz$range(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(HxzClient.capRange(cir.getReturnValue(),16,512));
    }
}
