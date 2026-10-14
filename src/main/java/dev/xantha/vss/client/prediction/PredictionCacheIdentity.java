package dev.xantha.vss.client.prediction;

import java.io.IOException;
import java.nio.file.Path;
import me.cortex.voxy.hxz.HxzIdentity;
import me.cortex.voxy.hxz.HxzSettings;
import me.cortex.voxy.hxz.core.TerrainFingerprint;
import dev.xantha.vss.config.VSSClientConfig;

/** Isolate generated predictions without moving, deleting or reidentifying real Voxy terrain. */
public final class PredictionCacheIdentity {
    private PredictionCacheIdentity(){}
    public static Path directory(Path original,ClientTerrainSampler sampler){
        if(!HxzSettings.CONFIG.predictionCacheIsolation)return original;
        var p=sampler.profile();
        String metadata="hxz-prediction-v3\n"+HxzSettings.CONFIG.stablePrediction+"\n"+HxzSettings.CONFIG.predictionStrictSampling+"\n"+HxzSettings.CONFIG.predictionSurfaceFeatures+"\n"+p.dimension()+"\n"+p.seed()+"\n"+p.minY()+"\n"+p.height()+"\n"+
            p.generatorType()+"\n"+p.generatorSettings()+"\n"+p.fingerprint()+"\n"+p.generatorCompression()+"\n"+p.generatorRawSize()+"\n"+
            sampler.getClass().getName()+"\n"+VSSClientConfig.CONFIG.predictionSupersample+"\n"+sampler.interiorTerrain();
        return original.resolve("hxz-profile-"+TerrainFingerprint.digest(metadata,p.generatorData()));
    }
    public static PredictionCacheMigration.Source findLegacy(Path base,Path legacy,ClientTerrainSampler sampler)throws IOException{
        // Old seed-only stores lack proof of their current generator settings.
        return HxzSettings.CONFIG.predictionCacheIsolation?null:PredictionCacheMigration.find(base,legacy,sampler);
    }
    public static String serverScope(String original,Path save){
        if(save!=null||!HxzSettings.CONFIG.worldIsolation||!HxzSettings.CONFIG.predictionCacheIsolation)return original;
        String namespace=HxzIdentity.clientNamespace.get();
        return namespace==null||namespace.isBlank()?original:"hxz-prediction:"+namespace;
    }
}
