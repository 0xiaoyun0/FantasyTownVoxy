package me.cortex.voxy.hxz.mixin;

import dev.xantha.vss.client.prediction.PredictionPressureRetirement;
import dev.xantha.vss.client.prediction.PredictionTileManager;
import dev.xantha.vss.client.prediction.PredictionTileManager.PredictionTile;
import dev.xantha.vss.client.prediction.PredictionTileManager.PredictionTileKey;
import dev.xantha.vss.client.prediction.VssLodLayout;
import java.util.Map;
import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value=PredictionTileManager.class,remap=false)
public abstract class PredictionPressureRetirementMixin {
    @Shadow @Final private Map<PredictionTileKey,PredictionTile> ready;
    @Shadow @Final private Set<PredictionTileKey> desiredKeys;
    @Shadow @Final private Set<PredictionTileKey> pending;
    @Shadow private double cameraBlockX;
    @Shadow private double cameraBlockZ;

    // This path already requires real memory pressure and uses the manager's normal
    // removeTile/resource/coverage-epoch lifecycle. Never change ordinary retirement.
    @Redirect(method="pruneReady",at=@At(value="INVOKE",
            target="Ldev/xantha/vss/client/prediction/PredictionTileResidencyPolicy;coveredByAncestor(Ljava/util/Set;Ldev/xantha/vss/client/prediction/VssLodLayout;Ldev/xantha/vss/client/prediction/PredictionTileManager$PredictionTileKey;)Z"))
    private boolean hxz$releaseObsoleteIndependentDetail(Set<PredictionTileKey> residents,VssLodLayout layout,
                                                        PredictionTileKey key) {
        return PredictionPressureRetirement.coveredOrObsolete(residents,layout,key,ready,desiredKeys,pending,
                cameraBlockX,cameraBlockZ);
    }
}
