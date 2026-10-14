package dev.xantha.vss.client.prediction;

import java.util.LinkedHashMap;
import java.util.Map;
import me.cortex.voxy.hxz.HxzSettings;
import dev.xantha.vss.client.prediction.PredictionTileManager.PredictionTile;
import dev.xantha.vss.client.prediction.PredictionTileManager.PredictionTileKey;

/** Filter geometry and ownership together; keep the planner's reusable samples intact. */
public final class PredictionQualityGate {
    private PredictionQualityGate() { }

    public static boolean detailed(PredictionTile tile) {
        return tile.cellAxis() >= VssLodLayout.TILE_QUADS
                && tile.spacingBlocks() > 0 && tile.spacingBlocks() <= 2;
    }

    public static Map<PredictionTileKey,PredictionTile> visible(Map<PredictionTileKey,PredictionTile> tiles) {
        if (!HxzSettings.CONFIG.qualityFirstLod) return tiles;
        var selected=new LinkedHashMap<PredictionTileKey,PredictionTile>();
        tiles.forEach((key,tile)->{if(detailed(tile))selected.put(key,tile);});
        return selected;
    }
}
