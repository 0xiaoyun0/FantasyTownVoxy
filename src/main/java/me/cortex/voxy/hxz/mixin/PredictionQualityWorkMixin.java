package me.cortex.voxy.hxz.mixin;

import dev.xantha.vss.client.prediction.PredictionTileManager;
import dev.xantha.vss.client.prediction.PredictionTileManager.PredictionTile;
import dev.xantha.vss.client.prediction.PredictionTileManager.PredictionTileKey;
import dev.xantha.vss.client.prediction.VssLodFocus;
import dev.xantha.vss.client.prediction.VssLodLayout;
import java.util.Map;
import java.util.Set;
import me.cortex.voxy.hxz.HxzSettings;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Finish useful, already-selected detail before spending the budget on hidden coverage. */
@Mixin(value = PredictionTileManager.class, remap = false)
public abstract class PredictionQualityWorkMixin {
    @Shadow private VssLodLayout layout;
    @Shadow @Final private Set<PredictionTileKey> terrainLeaves;
    @Shadow private Map<PredictionTileKey, Integer> terrainTargets;
    @Shadow @Final private Map<PredictionTileKey, PredictionTile> ready;
    @Shadow private double cameraBlockX;
    @Shadow private double cameraBlockZ;
    @Shadow private VssLodFocus buildFocus;
    @Shadow private boolean backgroundWork(PredictionTileKey key) { throw new AssertionError(); }

    @Unique
    private boolean hxz$qualityWork(PredictionTileKey key) {
        // Do not enlarge the planner's fine region or make the distant roots full-size.
        // Only the selected, complete 1–2-block grids can satisfy the render quality gate.
        return HxzSettings.CONFIG.qualityFirstLod && HxzSettings.CONFIG.prioritizeDetailedPrediction
                && key.lod() >= 0 && key.lod() <= 1
                && key.lod() < layout.levelCount() && terrainLeaves.contains(key)
                && terrainTargets.getOrDefault(key, 0) >= VssLodLayout.TILE_QUADS;
    }

    @Inject(method = "terrainParentReady", at = @At("HEAD"), cancellable = true)
    private void hxz$allowIndependentDetail(PredictionTileKey key, CallbackInfoReturnable<Boolean> cir) {
        if (hxz$qualityWork(key)) cir.setReturnValue(true);
    }

    @Inject(method = "mediumWorkAllowed", at = @At("HEAD"), cancellable = true)
    private void hxz$skipInvisibleCoverageDependency(PredictionTileKey key, boolean surface,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (hxz$qualityWork(key)) cir.setReturnValue(true);
    }

    @Inject(method = "mediumCoverageWork", at = @At("HEAD"), cancellable = true)
    private void hxz$keepDetailWorkerLimits(PredictionTileKey key, CallbackInfoReturnable<Boolean> cir) {
        // These jobs remain ordinary refinement and retain its existing frame/worker limits.
        if (hxz$qualityWork(key)) cir.setReturnValue(false);
    }

    @Inject(method = "workPriority", at = @At("HEAD"), cancellable = true)
    private void hxz$finishNearDetail(PredictionTileKey key, boolean surface, CallbackInfoReturnable<Integer> cir) {
        if (!hxz$qualityWork(key) || buildFocus != null) return;
        double span = layout.tileBlocks(key.lod());
        double dx = Math.max(0, Math.max(key.tileX() * span - cameraBlockX,
                cameraBlockX - (key.tileX() + 1.0) * span));
        double dz = Math.max(0, Math.max(key.tileZ() * span - cameraBlockZ,
                cameraBlockZ - (key.tileZ() + 1.0) * span));
        int band = Math.min(1500, (int) (Math.hypot(dx, dz) / 64));
        PredictionTile resident = ready.get(key);
        int stage = surface ? 3 : resident == null ? 2 : resident.cellAxis() < 32 ? 1 : 0;
        cir.setReturnValue((backgroundWork(key) ? 1_000_000 : 0) + 2000 + band * 4 + stage);
    }
}
