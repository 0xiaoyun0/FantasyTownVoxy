package me.cortex.voxy.hxz.mixin;

import me.cortex.voxy.hxz.client.HxzClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reuse VSS's existing leases; only defer optional work while foreground is under pressure. */
@Mixin(targets="dev.xantha.vss.client.prediction.PredictionCpuBudget",remap=false)
public class PredictionBudgetMixin {
    @Inject(method={"tryAcquire(ZZ)Ldev/xantha/vss/client/prediction/PredictionCpuBudget$Lease;","tryReserve(ZZ)Ldev/xantha/vss/client/prediction/PredictionCpuBudget$Reservation;"},at=@At("HEAD"),cancellable=true)
    private void hxz$admit(boolean urgent,boolean pausedDetail,CallbackInfoReturnable<Object> ci){
        if(!urgent&&HxzClient.deferPrediction())ci.setReturnValue(null);
    }
    @Inject(method="tryReserveQueued",at=@At("HEAD"),cancellable=true)
    private void hxz$queue(boolean urgent,boolean pausedDetail,int limit,CallbackInfoReturnable<Object> ci){
        if(!urgent&&HxzClient.deferPrediction())ci.setReturnValue(null);
    }
}
