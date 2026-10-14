package hxz.smoke;

import com.google.gson.*;
import com.mojang.serialization.*;
import dev.xantha.vss.client.prediction.*;
import dev.xantha.vss.networking.payloads.WorldgenProfileS2CPayload;
import dev.xantha.vss.networking.server.session.WorldgenProfileBuilder;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/** Independent prediction snapshot versus FULL generated chunks; no captured terrain fed to sampler. */
public final class TerrainAccuracyProbe {
    private static CompletableFuture<Void> running;
    public static boolean tick()throws Exception {
        if(running==null){
            running=CompletableFuture.runAsync(()->{try{run();}catch(Exception e){throw new CompletionException(e);}},
                command->{Thread t=new Thread(command,"HXZ accuracy fixture");t.setDaemon(true);t.start();});
            return false;
        }
        if(!running.isDone())return false;
        running.join();return true;
    }
    @SuppressWarnings("unchecked")
    private static void run()throws Exception {
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();
        if(server==null)throw new AssertionError("Accuracy fixture requires its private integrated server");
        Path directory=Path.of(System.getProperty("hxz.smoke.dir"));
        String mode=System.getProperty("hxz.smoke.accuracy");
        if(mode.equals("export")){
            Class<?> presetClass=Class.forName("raccoonman.reterraforged.data.worldgen.preset.settings.Preset");
            Object defaults=Class.forName("raccoonman.reterraforged.data.worldgen.preset.settings.Presets").getMethod("makeRTFDefault").invoke(null);
            Codec<Object> codec=(Codec<Object>)presetClass.getField("DIRECT_CODEC").get(null);
            for(String variant:List.of("rtf-default","rtf-custom")){
                JsonElement json=codec.encodeStart(JsonOps.INSTANCE,defaults).getOrThrow().deepCopy();
                if(variant.equals("rtf-custom")&&!customize(json))throw new AssertionError("Missing custom sea level");
                Object preset=codec.parse(JsonOps.INSTANCE,json).getOrThrow();
                Path dest=directory.resolve("exported").resolve(variant);Files.createDirectories(dest);
                var factory=Class.forName("raccoonman.reterraforged.data.worldgen.Datapacks").getMethod("makePreset",presetClass,RegistryAccess.class,Path.class,Path.class,String.class);
                var generator=(net.minecraft.data.DataGenerator)factory.invoke(null,preset,server.registryAccess(),dest.resolve("work"),dest.resolve("pack"),variant);
                generator.run();
            }
            System.out.println("HXZ_ACCURACY exported actual RTF default and sea-level +9 presets");return;
        }
        var payload=server.submit(()->WorldgenProfileBuilder.build(server,1,Level.OVERWORLD)).get(90,TimeUnit.SECONDS);
        var decoder=Class.forName("dev.xantha.vss.client.prediction.ClientWorldgenProfileDecoder").getDeclaredMethod("decode",WorldgenProfileS2CPayload.class,RegistryAccess.class);
        decoder.setAccessible(true);
        var samplers=(Map<?,ClientTerrainSampler>)decoder.invoke(null,payload,mc.level.registryAccess());
        ClientTerrainSampler sampler=samplers.get(Level.OVERWORLD);
        if(sampler==null)throw new AssertionError("No prediction sampler for accuracy fixture");
        long start=System.nanoTime();
        int count=0,previewGood=0,detailGood=0,stable=0,wet=0,wetGood=0,land=0,landGood=0,mountain=0,mountainGood=0,maxPreview=0,maxDetail=0;
        long totalError=0;
        List<int[]> centers=new ArrayList<>(List.of(new int[]{-192,-128},new int[]{-96,160},new int[]{0,0},new int[]{128,-96},new int[]{192,192},new int[]{-256,64}));
        JsonArray mismatches=new JsonArray();
        try(sampler){
            // Select additional high ground by predicted height only, before seeing reference terrain.
            // A fixed coordinate scan avoids accepting a sea-dominated score as a mountain test.
            for(int gz=-8;gz<=8&&centers.size()<10;gz++)for(int gx=-8;gx<=8&&centers.size()<10;gx++){
                int cx=gx*32,cz=gz*32;
                if(centers.stream().anyMatch(c->Math.abs(c[0]-cx)<32&&Math.abs(c[1]-cz)<32))continue;
                if(sampler.sampleForLod(cx*16,cz*16,16).surfaceY()>=sampler.seaLevel()+40)centers.add(new int[]{cx,cz});
            }
            if(centers.size()<10)throw new AssertionError("Fixture lacks four high-ground areas");
            for(int[] center:centers)for(int dz=0;dz<2;dz++)for(int dx=0;dx<2;dx++){
                int cx=center[0]+dx,cz=center[1]+dz;
                // Predict first. The sampler never observes actual chunks or their captured height cache.
                List<ClientColumnSample> previews=new ArrayList<>(),details=new ArrayList<>();
                if(sampler.getClass().getSimpleName().equals("RustTerrainSampler")){
                    var grid=sampler.getClass().getDeclaredMethod("sampleGridInPlace",int.class,int.class,int.class,int.class,int.class,ClientColumnSample[].class,int.class,int.class,boolean.class);
                    grid.setAccessible(true);
                    ClientColumnSample[] samples=new ClientColumnSample[64];
                    grid.invoke(sampler,cx*16+1,cz*16+1,2,8,8,samples,0,8,true);
                    previews.addAll(Arrays.asList(samples));
                }
                for(int z=1;z<16;z+=2)for(int x=1;x<16;x+=2){
                    if(!sampler.getClass().getSimpleName().equals("RustTerrainSampler"))previews.add(sampler.samplePreview(cx*16+x,cz*16+z,16));
                    details.add(sampler.sampleForLod(cx*16+x,cz*16+z,1));
                }
                int[][] actual=server.submit(()->{
                    var level=server.overworld();var chunk=level.getChunk(cx,cz);int[][] columns=new int[64][4];int i=0;
                    for(int z=1;z<16;z+=2)for(int x=1;x<16;x+=2){
                        int water=0,kind=0,ground=level.getMinBuildHeight();
                        var pos=new BlockPos.MutableBlockPos(cx*16+x,0,cz*16+z);
                        for(int y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)+1;y>=level.getMinBuildHeight();y--){
                            var block=chunk.getBlockState(pos.setY(y));
                            if(block.isAir())continue;
                            if(!block.getFluidState().isEmpty()&&kind==0){kind=block.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)?2:1;water=y+1;}
                            if(vegetation(block)||block.is(Blocks.ICE)||block.is(Blocks.SNOW)||block.is(Blocks.SNOW_BLOCK)&&kind!=0)continue;
                            if(block.blocksMotion()&&block.getFluidState().isEmpty()){ground=y+1;break;}
                        }
                        columns[i++]=new int[]{ground,water,kind,net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(chunk.getBlockState(pos.setY(ground-1)).getBlock())};
                    }
                    return columns;
                }).get(90,TimeUnit.SECONDS);
                for(int i=0;i<64;i++){
                    var a=actual[i];var p=previews.get(i);var d=details.get(i);
                    int pe=Math.abs(p.surfaceY()-a[0]),de=Math.abs(d.surfaceY()-a[0]);
                    boolean pg=matches(p,a),dg=matches(d,a);
                    count++;if(pg)previewGood++;if(dg)detailGood++;
                    if(!pg&&a[2]==0&&mismatches.size()<12){
                        var mismatch=new JsonObject();mismatch.addProperty("x",cx*16+1+(i%8)*2);mismatch.addProperty("z",cz*16+1+(i/8)*2);
                        mismatch.addProperty("actualHeight",a[0]);mismatch.addProperty("predictedHeight",p.surfaceY());mismatch.addProperty("predictedWater",p.hasFluid());
                        mismatch.addProperty("actualBlock",net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(net.minecraft.core.registries.BuiltInRegistries.BLOCK.byId(a[3])).toString());mismatches.add(mismatch);
                    }
                    if(p.surfaceY()==d.surfaceY()&&p.fluid()==d.fluid()&&(!p.hasFluid()||p.fluidY()==d.fluidY()))stable++;
                    if(a[2]!=0){wet++;if(pg)wetGood++;}else{land++;if(pg)landGood++;}
                    if(a[0]>=sampler.seaLevel()+40){mountain++;if(pg)mountainGood++;}
                    maxPreview=Math.max(maxPreview,pe);maxDetail=Math.max(maxDetail,de);totalError+=pe;
                }
                System.out.println("HXZ_ACCURACY progress "+mode+" n="+count+" preview="+previewGood+" refined="+detailGood+" wet="+wet);
            }
        }
        JsonObject out=new JsonObject();out.addProperty("fixture",mode);out.addProperty("seed",payload.seed());out.addProperty("sampler",sampler.getClass().getSimpleName());
        out.addProperty("samples",count);out.addProperty("surfaceToleranceBlocks",2);out.addProperty("waterToleranceBlocks",1);
        out.addProperty("previewMatchPercent",100.0*previewGood/count);out.addProperty("refinedMatchPercent",100.0*detailGood/count);
        out.addProperty("previewRefinedIdenticalPercent",100.0*stable/count);out.addProperty("wetSamples",wet);out.addProperty("wetMatchPercent",wet==0?0:100.0*wetGood/wet);
        out.addProperty("landSamples",land);out.addProperty("landMatchPercent",100.0*landGood/land);out.addProperty("maxPreviewHeightError",maxPreview);out.addProperty("maxRefinedHeightError",maxDetail);
        out.addProperty("highGroundSamples",mountain);out.addProperty("highGroundMatchPercent",mountain==0?0:100.0*mountainGood/mountain);
        out.add("chunkAreaOrigins",new Gson().toJsonTree(centers));
        out.addProperty("meanPreviewHeightError",(double)totalError/count);out.addProperty("seconds",(System.nanoTime()-start)/1e9);
        out.add("landMismatchExamples",mismatches);
        Files.writeString(directory.resolve("accuracy.json"),new GsonBuilder().setPrettyPrinting().create().toJson(out));
        System.out.println("HXZ_ACCURACY result "+out);
        if(previewGood<count*.90||detailGood<count*.90||wet<128||wetGood<wet*.90||landGood<land*.90||mountain<128||mountainGood<mountain*.90)
            throw new AssertionError("Prediction surface/water/category match below 90%: "+out);
    }
    private static boolean matches(ClientColumnSample s,int[] actual){
        return Math.abs(s.surfaceY()-actual[0])<=2&&s.hasFluid()==(actual[2]!=0)
            &&(actual[2]==0||s.fluid()==actual[2]&&Math.abs(s.fluidY()-actual[1])<=1);
    }
    private static boolean vegetation(net.minecraft.world.level.block.state.BlockState b){
        return b.is(BlockTags.LEAVES)||b.is(BlockTags.LOGS)||b.is(Blocks.MANGROVE_ROOTS)||b.is(Blocks.MUDDY_MANGROVE_ROOTS)
            ||b.is(Blocks.BAMBOO)||b.is(Blocks.CACTUS)||b.is(Blocks.MUSHROOM_STEM)||b.is(Blocks.BROWN_MUSHROOM_BLOCK)||b.is(Blocks.RED_MUSHROOM_BLOCK);
    }
    private static boolean customize(JsonElement json){
        if(json.isJsonObject())for(var e:json.getAsJsonObject().entrySet()){
            if(e.getKey().replace("_","").equalsIgnoreCase("seaLevel")&&e.getValue().isJsonPrimitive()&&e.getValue().getAsJsonPrimitive().isNumber()){
                json.getAsJsonObject().addProperty(e.getKey(),e.getValue().getAsInt()+9);return true;
            }
            if(customize(e.getValue()))return true;
        }
        if(json.isJsonArray())for(var e:json.getAsJsonArray())if(customize(e))return true;
        return false;
    }
}
