package hxz.smoke.mixin;

import hxz.smoke.ExteriorCostTrace;
import java.util.function.BooleanSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Registered only by the --exterior-audit test fixture, never in a release JAR. */
@Mixin(targets = "dev.xantha.vss.client.prediction.PredictionJavaExterior", remap = false)
public final class ExteriorCostTraceMixin {
    @Inject(method = "sample", at = @At("HEAD"))
    private void hxz$begin(int x, int z, int step, int bottom, int top, BooleanSupplier valid,
                          CallbackInfoReturnable<boolean[]> cir) {
        ExteriorCostTrace.enter(this, x, z, step, bottom, top);
    }

    @Inject(method = "sample", at = @At("RETURN"))
    private void hxz$end(int x, int z, int step, int bottom, int top, BooleanSupplier valid,
                        CallbackInfoReturnable<boolean[]> cir) {
        ExteriorCostTrace.returned(cir.getReturnValue());
    }
}
