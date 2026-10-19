package hxz.smoke;

import dev.xantha.vss.client.prediction.PredictionTileManager;
import dev.xantha.vss.client.prediction.PredictionTileManager.PredictionTile;
import dev.xantha.vss.client.prediction.PredictionTileManager.PredictionTileKey;
import dev.xantha.vss.client.prediction.VssLodLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import me.cortex.voxy.hxz.HxzSettings;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;

/** Runs actual manager admission and priority methods without starting generation workers. */
public final class PredictionPriorityProbe {
    private static final Class<?> MANAGER = PredictionTileManager.class;

    private static void set(Object manager, String name, Object value) throws Exception {
        Field field = MANAGER.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    private static Object call(Object manager, String name, PredictionTileKey key, Boolean surface) throws Exception {
        Method method = surface == null
                ? MANAGER.getDeclaredMethod(name, PredictionTileKey.class)
                : MANAGER.getDeclaredMethod(name, PredictionTileKey.class, boolean.class);
        method.setAccessible(true);
        return surface == null ? method.invoke(manager, key) : method.invoke(manager, key, surface);
    }

    public static void verify() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        Object manager = unsafe.allocateInstance(MANAGER);
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"));
        VssLodLayout layout = VssLodLayout.of(2048, 1, true, true);
        var near = new PredictionTileKey(dimension, 2, 0, 0);
        var further = new PredictionTileKey(dimension, 5, 0, 0);
        var parent = new PredictionTileKey(dimension, 0, 0, layout.levelCount() - 1);
        Set<PredictionTileKey> leaves = Set.of(near, further);
        var ready = new HashMap<PredictionTileKey, PredictionTile>();
        set(manager, "layout", layout);
        set(manager, "dimension", dimension);
        set(manager, "ready", ready);
        set(manager, "terrainLeaves", leaves);
        set(manager, "terrainTargets", Map.of(near, 64, further, 64));
        set(manager, "ordinaryTargets", Map.of(near, 64, further, 64));
        set(manager, "idleTargets", Map.of());
        set(manager, "transitionTargets", Map.of());
        set(manager, "surfaceDesired", Set.of());
        set(manager, "dirtyTiles", Set.of());
        set(manager, "mediumSinceSurface", new AtomicInteger());
        set(manager, "mediumCoveragePending", true);
        Class<?> coverage = Class.forName("dev.xantha.vss.client.prediction.PredictionMediumCoverage");
        Method plan = coverage.getDeclaredMethod("plan", List.class, VssLodLayout.class);
        plan.setAccessible(true);
        set(manager, "mediumCoverage", plan.invoke(null, List.copyOf(leaves), layout));
        boolean original = HxzSettings.CONFIG.qualityFirstLod;
        try {
            HxzSettings.CONFIG.qualityFirstLod = false;
            if ((boolean) call(manager, "terrainParentReady", near, null))
                throw new AssertionError("Baseline must require an unbuilt parent");
            if ((boolean) call(manager, "mediumWorkAllowed", near, false))
                throw new AssertionError("Baseline must wait for medium coverage");
            int legacyNear = (int) call(manager, "workPriority", near, false);
            int legacyParent = (int) call(manager, "workPriority", parent, false);
            if (legacyNear <= legacyParent) throw new AssertionError("Baseline should build horizon before nearby detail");
            System.out.println("HXZ_PRIORITY_BASELINE parentReady=false mediumAllowed=false nearPriority="
                    + legacyNear + " parentPriority=" + legacyParent);

            HxzSettings.CONFIG.qualityFirstLod = true;
            if (!(boolean) call(manager, "terrainParentReady", near, null))
                throw new AssertionError("Quality-eligible leaf is still blocked by an invisible parent");
            if (!(boolean) call(manager, "mediumWorkAllowed", near, false))
                throw new AssertionError("Quality-eligible leaf is still blocked by invisible medium coverage");
            if ((int) call(manager, "targetCellAxis", near, null) != 64)
                throw new AssertionError("Quality leaf does not target a complete grid");
            int nearPriority = (int) call(manager, "workPriority", near, false);
            int farPriority = (int) call(manager, "workPriority", further, false);
            int parentPriority = (int) call(manager, "workPriority", parent, false);
            if (nearPriority >= parentPriority || nearPriority >= farPriority)
                throw new AssertionError("Quality work does not precede hidden parent/further work");
            var unfinished = new PredictionTile(near, new int[0], new int[0], null, null, null, 0, 0, 32, 2);
            ready.put(near, unfinished);
            if ((int) call(manager, "workPriority", near, false) >= nearPriority)
                throw new AssertionError("Existing fine work is not completed before starting another same-band grid");
            var nonLeaf = new PredictionTileKey(dimension, -100, -100, 0);
            if ((boolean) call(manager, "terrainParentReady", nonLeaf, null))
                throw new AssertionError("Policy generated an unselected fine tile");
            Field independent = HxzSettings.class.getField("prioritizeDetailedPrediction");
            boolean originalPriority = independent.getBoolean(HxzSettings.CONFIG);
            try {
                independent.setBoolean(HxzSettings.CONFIG, false);
                if ((boolean) call(manager, "terrainParentReady", further, null)
                        || (boolean) call(manager, "mediumWorkAllowed", further, false)
                        || (int) call(manager, "workPriority", further, false) < legacyParent)
                    throw new AssertionError("Independent scheduling opt-out did not restore original planner");
                if (!HxzSettings.CONFIG.qualityFirstLod)
                    throw new AssertionError("Scheduling opt-out weakened rendering quality");
            } finally {
                independent.setBoolean(HxzSettings.CONFIG, originalPriority);
            }
            verifyParentlessOwnership(dimension, layout);
            System.out.println("HXZ_PRIORITY PASS actual manager: bypass hidden ancestors for selected fine leaves, "
                    + "complete near grids first, preserve native admission outside quality work and explicit opt-out");
        } finally {
            HxzSettings.CONFIG.qualityFirstLod = original;
        }
    }

    private static void verifyParentlessOwnership(ResourceKey<Level> dimension, VssLodLayout layout) throws Exception {
        var meshType = dev.xantha.vss.client.prediction.PredictionMesh.class;
        var meshCtor = Arrays.stream(meshType.getDeclaredConstructors())
                .filter(c -> c.getParameterCount() == 10).findFirst().orElseThrow();
        meshCtor.setAccessible(true);
        var mesh = (dev.xantha.vss.client.prediction.PredictionMesh) meshCtor.newInstance(
                new float[0], new float[0], new int[0], new float[0], new float[0], new int[0],
                new boolean[64 * 64], 0, 0, 64 * 64);
        var tile = new PredictionTile(new PredictionTileKey(dimension, 2, 0, 0),
                new int[65 * 65], new int[65 * 65], null, mesh, null, 0, 1, 64, 1);
        var snapshotType = Class.forName("dev.xantha.vss.client.prediction.PredictionTileManager$RenderSnapshot");
        var snapshotCtor = Arrays.stream(snapshotType.getDeclaredConstructors())
                .filter(c -> c.getParameterCount() == 6).findFirst().orElseThrow();
        snapshotCtor.setAccessible(true);
        Object source = snapshotCtor.newInstance(dimension, layout, Map.of(tile.key(), tile), Map.of(), null, Set.of());
        var residencyType = Class.forName("dev.xantha.vss.client.prediction.PredictionRenderResidency");
        var residencyCtor = residencyType.getDeclaredConstructor(); residencyCtor.setAccessible(true);
        Object residency = residencyCtor.newInstance();
        var retain = residencyType.getDeclaredMethod("retain", snapshotType); retain.setAccessible(true);
        retain.invoke(residency, source);
        var uploaded = residencyType.getDeclaredMethod("uploaded", PredictionTile.class); uploaded.setAccessible(true);
        uploaded.invoke(residency, tile);
        var snapshot = residencyType.getDeclaredMethod("snapshot", snapshotType); snapshot.setAccessible(true);
        Object uploadedSnapshot = snapshot.invoke(residency, source);
        var covering = snapshotType.getDeclaredMethod("coveringTile", int.class, int.class, int.class);
        covering.setAccessible(true);
        if (covering.invoke(uploadedSnapshot, 8, 0, 0) != tile)
            throw new AssertionError("An uploaded parentless fine tile was unreachable");
        var resolve = dev.xantha.vss.client.prediction.PredictionRenderer.class.getDeclaredMethod("resolveCoverage",
                PredictionTile.class, int.class, int.class, double.class, snapshotType,
                dev.xantha.vss.client.prediction.VssLodFocus.class);
        resolve.setAccessible(true);
        Object coverage = resolve.invoke(null, tile, 0, 0, 1.0, uploadedSnapshot, null);
        var rendered = coverage.getClass().getDeclaredMethod("rendered"); rendered.setAccessible(true);
        if ((long) rendered.invoke(coverage) != 64L * 64)
            throw new AssertionError("Actual renderer excluded parentless fine terrain");
        var policy = Class.forName("dev.xantha.vss.client.prediction.PredictionTileResidencyPolicy");
        var retire = policy.getDeclaredMethod("shouldRetirePinned", PredictionTileKey.class,
                Set.class, Set.class, VssLodLayout.class, int.class, int.class);
        retire.setAccessible(true);
        if ((boolean) retire.invoke(null, tile.key(), Set.of(tile.key()), Set.of(tile.key()), layout, 0, 0))
            throw new AssertionError("Selected parentless fine terrain was retired");
        System.out.println("HXZ_PARENTLESS PASS actual GPU-residency snapshot and renderer ownership: 4096/4096 cells selected");
    }

    public static void main(String[] args) throws Exception { verify(); }
}
