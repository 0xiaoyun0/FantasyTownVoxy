package dev.xantha.vss.client.prediction;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;

/** Exact real exterior iterator: quantify repeated ordered queries without changing any sampling. */
public final class ExteriorRepeatTest {
    public static void main(String[] args) throws Exception {
        if (net.neoforged.fml.loading.FMLPaths.GAMEDIR.get() == null)
            net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(java.nio.file.Path.of("build", "exterior-repeat"));
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        AtomicLong evaluations = new AtomicLong();
        DensityFunction opaque = new DensityFunction.SimpleFunction() {
            @Override public double compute(DensityFunction.FunctionContext point) {
                evaluations.incrementAndGet();
                return point.blockY() == 160 || point.blockY() >= 71 && point.blockY() < 98 ? 1 : -1;
            }
            @Override public double minValue() { return -1; }
            @Override public double maxValue() { return 1; }
            @Override public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
                throw new UnsupportedOperationException();
            }
        };
        var zero = DensityFunctions.zero();
        var router = new NoiseRouter(zero, zero, zero, zero, zero, zero, zero, zero, zero, zero,
                zero, opaque, zero, zero, zero);
        var settings = new NoiseGeneratorSettings(NoiseSettings.create(-64, 256, 1, 2),
                Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(), router,
                SurfaceRules.state(Blocks.STONE.defaultBlockState()), List.of(), -64, false, false, false, false);
        var lookup = VanillaRegistries.createLookup();
        var source = new FixedBiomeSource(lookup.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS));
        var generator = new NoiseBasedChunkGenerator(source, Holder.direct(settings));
        var random = RandomState.create(settings, lookup.lookupOrThrow(Registries.NOISE), 42L);
        var sampler = new PredictionJavaExterior(generator, random, LevelHeightAccessor.create(-64, 256));
        if (!sampler.ordered() || sampler.reusesContexts()) throw new AssertionError("Opaque-node fixture lost ordered traversal");
        for (int top : new int[]{161, 150}) {
            for (int step : new int[]{4, 2, 1}) {
                evaluations.set(0);
                var first = sampler.sample(3080, -4088, step, 47, top, () -> true);
                long one = evaluations.get();
                for (int i = 0; i < 7; i++)
                    if (!Arrays.equals(first, sampler.sample(3080, -4088, step, 47, top, () -> true)))
                        throw new AssertionError("Repeated exact occupancy changed");
                System.out.println("EXTERIOR_REPEAT top=" + top + " step=" + step + " validRoof=" + (first != null)
                        + " oneQueryEvaluations=" + one + " eightQueriesEvaluations=" + evaluations.get());
                if (one <= 0 || evaluations.get() != one * 8)
                    throw new AssertionError("Repeated traversal cost changed; inspect before proposing a new cache");
            }
        }
        System.out.println("PASS exact iterator repeats identical density work; real RTF duplicate rate is still unmeasured");
    }
}
