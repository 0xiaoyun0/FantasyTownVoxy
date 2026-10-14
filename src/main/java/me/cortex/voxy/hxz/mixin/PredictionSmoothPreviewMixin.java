package me.cortex.voxy.hxz.mixin;

import dev.xantha.vss.client.prediction.*;
import java.util.function.BooleanSupplier;
import me.cortex.voxy.hxz.HxzSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=PredictionMeshBuilder.class,remap=false)
public class PredictionSmoothPreviewMixin {
    @Inject(method="buildForRenderingWithCityPreview",at=@At("HEAD"),cancellable=true)
    private static void hxz$coarseSurface(ClientColumnSample[] samples,int[] colors,int seaLevel,int fluidColor,
            int step,int grid,int[] foliage,int[] water,int baseX,int baseZ,@Coerce Object vegetation,
            @Coerce Object simple,@Coerce Object cities,BooleanSupplier current,CallbackInfoReturnable<PredictionMesh> ci){
        if(!HxzSettings.CONFIG.smoothPredictionPreview)return;
        var mesh=PredictionSmoothPreview.build(samples,colors,water,fluidColor,step,grid,vegetation,simple,cities,current);
        if(mesh!=null)ci.setReturnValue(mesh);
    }
}
