package me.cortex.voxy.hxz.mixin;

import java.util.Map;
import dev.xantha.vss.client.prediction.PredictionQualityGate;
import dev.xantha.vss.client.prediction.PredictionTileManager.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** All normal/Iris draw lists and ownership indexes see the same quality selection. */
@Mixin(targets="dev.xantha.vss.client.prediction.PredictionTileManager$RenderSnapshot",remap=false)
public class PredictionQualitySnapshotMixin {
    @ModifyVariable(method="<init>(Lnet/minecraft/resources/ResourceKey;Ldev/xantha/vss/client/prediction/VssLodLayout;Ljava/util/Map;Ljava/util/Map;Ljava/util/Set;Ljava/util/Set;)V",
            at=@At("HEAD"),ordinal=0,argsOnly=true)
    private static Map<PredictionTileKey,PredictionTile> hxz$visibleTiles(Map<PredictionTileKey,PredictionTile> tiles) {
        return PredictionQualityGate.visible(tiles);
    }
}
