package me.cortex.voxy.hxz.mixin;

import dev.xantha.vss.client.prediction.ClientColumnSample;
import dev.xantha.vss.client.prediction.ClientTerrainSampler;
import me.cortex.voxy.hxz.HxzSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fewer mesh vertices are allowed; changing the underlying terrain between stages is optional. */
@Mixin(value=ClientTerrainSampler.class,remap=false)
public class StablePredictionMixin {
    @Unique private volatile net.minecraft.world.level.biome.BiomeManager hxz$biomes;
    @Inject(method="resolveSurface",at=@At("RETURN"),cancellable=true)
    private void hxz$surface(ClientColumnSample input,int x,int z,ClientTerrainSampler.TerrainFunction terrain,CallbackInfoReturnable<ClientColumnSample> ci){
        if(!HxzSettings.CONFIG.predictionSurfaceFeatures)return;
        var sampler=(ClientTerrainSampler)(Object)this;
        var biomes=hxz$biomes;
        if(biomes==null)hxz$biomes=biomes=dev.xantha.vss.client.prediction.PredictionSurfaceFeatures.biomes(sampler);
        ci.setReturnValue(dev.xantha.vss.client.prediction.PredictionSurfaceFeatures.apply(sampler,biomes,ci.getReturnValue(),x,z));
    }
    @Inject(method="samplePreview(III)Ldev/xantha/vss/client/prediction/ClientColumnSample;",at=@At("HEAD"),cancellable=true)
    private void hxz$preview(int x,int z,int spacing,CallbackInfoReturnable<ClientColumnSample> ci){
        if(HxzSettings.CONFIG.stablePrediction){
            // Dispatch to RTF's override, which restores its filtered context even inside a coarse scope.
            ci.setReturnValue(((ClientTerrainSampler)(Object)this).sampleForLod(x,z,spacing));
        }
    }
    @Inject(method="samplePreview(II)Ldev/xantha/vss/client/prediction/ClientColumnSample;",at=@At("HEAD"),cancellable=true)
    private void hxz$preview(int x,int z,CallbackInfoReturnable<ClientColumnSample> ci){
        if(HxzSettings.CONFIG.stablePrediction)ci.setReturnValue(((ClientTerrainSampler)(Object)this).sampleForLod(x,z,1));
    }
}
