package dev.xantha.vss.client.prediction;

import java.lang.invoke.*;
import java.lang.reflect.Method;
import java.util.Arrays;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.levelgen.SurfaceSystem;

/** Run Minecraft's missing surface extensions on an isolated column, never on a live world. */
public final class PredictionSurfaceFeatures {
    private PredictionSurfaceFeatures(){}
    public static BiomeManager biomes(ClientTerrainSampler sampler){
        return new BiomeManager(sampler::noiseBiome,BiomeManager.obfuscateSeed(sampler.profile().seed()));
    }
    public static ClientColumnSample apply(ClientTerrainSampler sampler,BiomeManager biomes,ClientColumnSample sample,int x,int z){
        if(sampler.randomStateContext()==null||!sample.hasSurface()||sample.volume()!=null)return sample;
        int top=Math.max(sample.surfaceY(),sample.hasFluid()?sample.fluidY():sample.surfaceY());
        var settings=sampler.generatorContext().generatorSettings().value();
        var biome=biomes.getBiome(new BlockPos(x,settings.useLegacyRandomSource()?0:top,z));
        boolean frozen=biome.is(Biomes.FROZEN_OCEAN)||biome.is(Biomes.DEEP_FROZEN_OCEAN);
        boolean badlands=biome.is(Biomes.ERODED_BADLANDS);
        if(!frozen&&!badlands)return sample;
        var column=new Column(sample,settings.defaultBlock());
        int min=sampler.profile().minY(),height=sampler.profile().height();
        try{
            if(frozen&&Access.ICE!=null)Access.ICE.invokeExact(sampler.randomStateContext().surfaceSystem(),min,(Biome)biome.value(),(BlockColumn)column,new BlockPos.MutableBlockPos(),x,z,top);
            if(badlands&&Access.BADLANDS!=null)Access.BADLANDS.invokeExact(sampler.randomStateContext().surfaceSystem(),(BlockColumn)column,x,z,top,LevelHeightAccessor.create(min,height));
        }catch(RuntimeException|Error failure){throw failure;}
        catch(Throwable failure){throw new IllegalStateException("Prediction surface extension failed",failure);}
        if(column.added.isEmpty())return sample;
        int max=Math.min(min+height,Math.max(top,column.top));
        var volume=PredictionColumnVolume.sample(min,max-min,y->{
            var state=column.getBlock(y);return state.isAir()?-1:BuiltInRegistries.BLOCK.getId(state.getBlock());
        },block->{var state=BuiltInRegistries.BLOCK.byId(block).defaultBlockState();return state.getFluidState().isEmpty()?0:state.is(Blocks.LAVA)?2:1;});
        var shape=volume.asSample();
        // Preserve biome and decoration identity while using the actual added ice/rock spans.
        return new ClientColumnSample(shape.surfaceY(),shape.fluidY(),sample.biomeIndex(),frozen?shape.topBlockIndex():sample.topBlockIndex(),
            sample.structureIndex(),sample.treeKind(),sample.treeDensity(),sample.treeHeight(),shape.fluid(),shape.flags(),sample.groundFeatureKind(),
            frozen?shape.underBlockIndex():sample.underBlockIndex(),sample.deepBlockIndex(),shape.surfaceBottom(),shape.lowerTop(),shape.lowerBottom(),shape.spanFloor(),volume);
    }
    private static final class Column implements BlockColumn {
        private final ClientColumnSample sample;
        private final BlockState solid;
        private final Int2ObjectOpenHashMap<BlockState> added=new Int2ObjectOpenHashMap<>();
        private int top;
        Column(ClientColumnSample sample,BlockState solid){this.sample=sample;this.solid=solid;top=sample.surfaceY();}
        @Override public BlockState getBlock(int y){
            var state=added.get(y);if(state!=null)return state;
            if(y<sample.surfaceY())return solid;
            if(sample.hasFluid()&&y<sample.fluidY())return (sample.fluid()==2?Blocks.LAVA:Blocks.WATER).defaultBlockState();
            return Blocks.AIR.defaultBlockState();
        }
        @Override public void setBlock(int y,BlockState state){added.put(y,state);top=Math.max(top,y+1);}
    }
    private static final class Access {
        static final MethodHandle ICE=find(int.class,Biome.class,BlockColumn.class,BlockPos.MutableBlockPos.class,int.class,int.class,int.class);
        static final MethodHandle BADLANDS=find(BlockColumn.class,int.class,int.class,int.class,LevelHeightAccessor.class);
        private static MethodHandle find(Class<?>... parameters){
            try{
                Method method=Arrays.stream(SurfaceSystem.class.getDeclaredMethods()).filter(m->m.getReturnType()==void.class&&Arrays.equals(m.getParameterTypes(),parameters)).findFirst().orElseThrow(()->new NoSuchMethodException("SurfaceSystem extension"));
                method.setAccessible(true);return MethodHandles.lookup().unreflect(method);
            }catch(ReflectiveOperationException|RuntimeException unavailable){
                me.cortex.voxy.common.Logger.warn("Prediction surface extension unavailable; keeping base terrain",unavailable);return null;
            }
        }
    }
}
