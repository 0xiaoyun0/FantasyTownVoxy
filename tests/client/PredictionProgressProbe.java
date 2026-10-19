package hxz.smoke;

import dev.xantha.vss.client.prediction.ClientPredictionState;
import dev.xantha.vss.client.prediction.PredictionRenderer;
import dev.xantha.vss.client.prediction.PredictionTileManager;
import dev.xantha.vss.client.prediction.PredictionTileManager.PredictionTile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/** An RTF runtime test must deliver detailed geometry, not merely hide every coarse tile. */
public final class PredictionProgressProbe {
    private static long started, nextSample, firstAcceptedMs = -1, firstDrawMs = -1;
    private static int acceptedMax, coarseMax;
    private static boolean drawn;
    private static Method renderSnapshot, tiles;
    private static int phase;
    private static long nextReport;
    private static long sampledPeakHeapBytes;
    private static long initialMeshUploads, initialRendered;
    private static String finalRenderer = "";
    private static volatile boolean positioned;
    private static volatile Throwable failure;
    private static long auditWaitStarted, nextAuditWaitReport;
    private static boolean auditSummaryPrinted;
    private static Field managers;

    /** Minimal launcher contract: call once per client tick after a single-player world joins. */
    public static boolean tick() throws Exception {
        try {
            return tickWindow();
        } catch (Exception | Error error) {
            printAuditSummary();
            throw error;
        }
    }

    private static boolean tickWindow() throws Exception {
        var minecraft = Minecraft.getInstance();
        if (failure != null) throw new AssertionError("RTF progress scene setup failed", failure);
        if (phase == 0) {
            minecraft.options.renderDistance().set(2);
            minecraft.options.fov().set(70);
            minecraft.options.hideGui = true;
            minecraft.options.bobView().set(false);
            minecraft.options.entityShadows().set(false);
            minecraft.options.broadcastOptions();
            minecraft.getSingleplayerServer().execute(() -> {
                try {
                    var server = minecraft.getSingleplayerServer();
                    var level = server.overworld();
                    if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator))
                        throw new AssertionError("Progress test requires actual RTF noise terrain");
                    level.setDayTime(6000);
                    level.setWeatherParameters(100000, 0, false, false);
                    level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
                    var player = server.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.SPECTATOR);
                    level.getChunk(3080 >> 4, -4088 >> 4);
                    int height = level.getHeight(Heightmap.Types.WORLD_SURFACE, 3080, -4088);
                    player.teleportTo(level, 3080, height + 12, -4088, java.util.Set.of(), 0, 25);
                    positioned = true;
                } catch (Throwable error) { failure = error; }
            });
            phase = 1;
            return false;
        }
        if (!positioned || Math.abs(minecraft.player.getX() - 3080) > 1
                || Math.abs(minecraft.player.getZ() + 4088) > 1) return false;
        if (phase == 1) {
            // Diagnostic timing starts after the real dimension sampler is installed.
            // The official progress fixture continues to include initialization time.
            if (Boolean.getBoolean("hxz.smoke.exteriorAudit") && !auditProfileReady()) return false;
            begin(); nextReport = 0; phase = 2;
            System.out.println("HXZ_FINE_START actual RTF mountain=(3080,-4088) vanillaRadius=2 "
                    + "qualityFirst=" + me.cortex.voxy.hxz.HxzSettings.CONFIG.qualityFirstLod);
        }
        minecraft.player.setYRot(0); minecraft.player.setXRot(25);
        minecraft.player.setDeltaMovement(0, 0, 0);
        sample();
        long elapsed = (System.nanoTime() - started) / 1_000_000L;
        if (elapsed >= nextReport) {
            System.out.println(summary() + " elapsedMs=" + elapsed);
            System.out.println("HXZ_FINE_DIAGNOSTICS " + ClientPredictionState.diagnostics());
            nextReport = elapsed + 30_000;
        }
        long window = measurementWindow();
        if (elapsed < window) return false;
        finalRenderer = PredictionRenderer.diagnostics();
        System.out.println("HXZ_FINE_WINDOW_END proofVersion=2 elapsedMs=" + elapsed
                + " meshUploads=" + counter(finalRenderer, "meshUploads")
                + " rendered=" + counter(finalRenderer, "rendered"));
        System.out.println("HXZ_FINE_COST sampledPeakHeapMiB=" + sampledPeakHeapBytes / 1_048_576L
                + " " + PredictionFrameMetrics.finish());
        System.out.println("HXZ_FINE_RENDER_FINAL " + finalRenderer);
        // A failed draw assertion must not discard the completed sampling evidence.
        printAuditSummary();
        if (Boolean.getBoolean("hxz.smoke.predictionProgressBaseline")) {
            System.out.println("HXZ_FINE_PROGRESS BASELINE " + summary());
        } else requireDrawn();
        net.minecraft.client.Screenshot.grab(minecraft.gameDirectory, "prediction-progress-final.png",
                minecraft.getMainRenderTarget(), component -> {});
        return true;
    }

    private static boolean auditProfileReady() throws Exception {
        long now = System.nanoTime();
        if (auditWaitStarted == 0) auditWaitStarted = now;
        if (managers == null) {
            managers = ClientPredictionState.class.getDeclaredField("MANAGERS");
            managers.setAccessible(true);
        }
        var dimension = Minecraft.getInstance().level.dimension();
        Object found = ((Map<?, ?>) managers.get(null)).get(dimension);
        long elapsed = (now - auditWaitStarted) / 1_000_000L;
        if (found instanceof PredictionTileManager manager && manager.exactWorldgen()) {
            System.out.println("HXZ_EXTERIOR_AUDIT_START profileWaitMs=" + elapsed
                    + " windowMs=" + measurementWindow() + " sampler=" + manager.sampler().getClass().getName());
            return true;
        }
        long limit = Math.max(30_000L, Math.min(900_000L,
                Long.getLong("hxz.smoke.exteriorAuditProfileWaitMs", 300_000L)));
        if (elapsed >= limit) throw new AssertionError("Exterior audit has no installed exact sampler after " + elapsed + " ms");
        if (now >= nextAuditWaitReport) {
            System.out.println("HXZ_EXTERIOR_AUDIT_WAIT elapsedMs=" + elapsed + " " + ClientPredictionState.diagnostics());
            nextAuditWaitReport = now + 30_000_000_000L;
        }
        return false;
    }

    private static long measurementWindow() {
        if (Boolean.getBoolean("hxz.smoke.exteriorAudit")) {
            return Math.max(60_000L, Math.min(900_000L,
                    Long.getLong("hxz.smoke.exteriorAuditWindowMs", 180_000L)));
        }
        return Math.max(60_000L, Math.min(180_000L,
                Long.getLong("hxz.smoke.predictionProgressWindowMs", 180_000L)));
    }

    private static void printAuditSummary() {
        if (!Boolean.getBoolean("hxz.smoke.exteriorAudit") || auditSummaryPrinted) return;
        auditSummaryPrinted = true;
        System.out.println(ExteriorCostTrace.summary());
    }

    public static void begin() {
        started = System.nanoTime(); nextSample = 0;
        firstAcceptedMs = firstDrawMs = -1;
        acceptedMax = coarseMax = 0; drawn = false;
        sampledPeakHeapBytes = 0;
        String initialRenderer = PredictionRenderer.diagnostics();
        initialMeshUploads = counter(initialRenderer, "meshUploads");
        initialRendered = counter(initialRenderer, "rendered");
        System.out.println("HXZ_FINE_WINDOW_START proofVersion=2 windowMs=" + measurementWindow()
                + " meshUploads=" + initialMeshUploads + " rendered=" + initialRendered);
        PredictionFrameMetrics.begin();
    }

    public static boolean sample() throws Exception {
        long now = System.nanoTime();
        if (started == 0) begin();
        if (now < nextSample) return drawn;
        nextSample = now + 1_000_000_000L;
        Runtime runtime = Runtime.getRuntime();
        sampledPeakHeapBytes = Math.max(sampledPeakHeapBytes, runtime.totalMemory() - runtime.freeMemory());
        var level = Minecraft.getInstance().level;
        if (level == null) return false;
        if (renderSnapshot == null) {
            renderSnapshot = ClientPredictionState.class.getDeclaredMethod("renderSnapshot", ResourceKey.class);
            renderSnapshot.setAccessible(true);
        }
        Object snapshot = renderSnapshot.invoke(null, level.dimension());
        if (snapshot == null) return false;
        if (tiles == null) { tiles = snapshot.getClass().getDeclaredMethod("tiles"); tiles.setAccessible(true); }
        Map<?, ?> accepted = (Map<?, ?>) tiles.invoke(snapshot);
        for (Object value : accepted.values()) {
            var tile = (PredictionTile) value;
            if (tile.cellAxis() < 64 || tile.spacingBlocks() < 1 || tile.spacingBlocks() > 2)
                throw new AssertionError("Progress policy exposed coarse geometry: " + tile.key());
        }
        int coarse = (int) ClientPredictionState.readyTiles(level.dimension()).stream()
                .filter(tile -> tile.cellAxis() < 64 || tile.spacingBlocks() > 2).count();
        acceptedMax = Math.max(acceptedMax, accepted.size()); coarseMax = Math.max(coarseMax, coarse);
        long elapsed = (now - started) / 1_000_000L;
        if (!accepted.isEmpty() && firstAcceptedMs < 0) {
            firstAcceptedMs = elapsed;
            System.out.println("HXZ_FINE_FIRST acceptedMs=" + elapsed + " accepted=" + accepted.size()
                    + " coarseHidden=" + coarse);
        }
        String diagnostics = PredictionRenderer.diagnostics();
        boolean actualDraw = !accepted.isEmpty() && counter(diagnostics, "meshUploads") > initialMeshUploads
                && counter(diagnostics, "rendered") > initialRendered;
        if (actualDraw && firstDrawMs < 0) {
            firstDrawMs = elapsed; drawn = true;
            System.out.println("HXZ_FINE_FIRST drawnMs=" + elapsed + " accepted=" + accepted.size()
                    + " meshUploads=" + counter(diagnostics, "meshUploads")
                    + " rendered=" + counter(diagnostics, "rendered")
                    + " windowUploads=" + (counter(diagnostics, "meshUploads") - initialMeshUploads)
                    + " windowRendered=" + (counter(diagnostics, "rendered") - initialRendered));
        }
        return drawn;
    }

    private static long counter(String diagnostics, String key) {
        var found = Pattern.compile("(?:^|,)" + key + "=(\\d+)").matcher(diagnostics);
        return found.find() ? Long.parseLong(found.group(1)) : 0;
    }

    public static String summary() {
        return "HXZ_FINE_PROGRESS acceptedMax=" + acceptedMax + " coarseHiddenMax=" + coarseMax
                + " firstAcceptedMs=" + firstAcceptedMs + " firstDrawMs=" + firstDrawMs;
    }

    public static void requireDrawn() throws Exception {
        sample();
        if (!drawn) throw new AssertionError("No detailed RTF prediction reached a draw: " + summary()
                + "\n" + ClientPredictionState.diagnostics());
        System.out.println("HXZ_FINE_PROGRESS PASS " + summary());
    }
}
