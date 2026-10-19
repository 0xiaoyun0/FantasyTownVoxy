package dev.xantha.vss.client.prediction;

import dev.xantha.vss.client.prediction.PredictionTileManager.PredictionTile;
import dev.xantha.vss.client.prediction.PredictionTileManager.PredictionTileKey;
import java.util.Map;
import java.util.Set;
import me.cortex.voxy.hxz.HxzSettings;

/** Pressure-only retirement of obsolete independent detail; never removes disk terrain. */
public final class PredictionPressureRetirement {
    private PredictionPressureRetirement() { }

    public static boolean coveredOrObsolete(Set<PredictionTileKey> residentKeys, VssLodLayout layout,
                                            PredictionTileKey key, Map<PredictionTileKey, PredictionTile> ready,
                                            Set<PredictionTileKey> desired, Set<PredictionTileKey> pending,
                                            double cameraX, double cameraZ) {
        if (PredictionTileResidencyPolicy.coveredByAncestor(residentKeys, layout, key)) return true;
        if (!HxzSettings.CONFIG.qualityFirstLod || !HxzSettings.CONFIG.prioritizeDetailedPrediction
                || desired.contains(key) || pending.contains(key) || key.lod() < 0 || key.lod() > 1)
            return false;
        PredictionTile tile=ready.get(key);
        // Intermediate 16/32 grids are hidden by the quality gate, but can be
        // left behind by movement before their selected 64 grid finishes too.
        return tile!=null && tile.cellAxis()>0
                && PredictionTileResidencyPolicy.outOfRetirementRange(key,layout,
                        (int)Math.floor(cameraX),(int)Math.floor(cameraZ));
    }
}
