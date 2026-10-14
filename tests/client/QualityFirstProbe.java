package hxz.smoke;

import dev.xantha.vss.client.prediction.*;
import dev.xantha.vss.client.prediction.PredictionTileManager.*;
import java.util.*;
import net.minecraft.world.level.Level;

/** Test the real snapshot consumed by both normal and Iris rendering, including cached tiles. */
public final class QualityFirstProbe {
    private static PredictionTile tile(int x,int lod,int axis,int spacing){
        return new PredictionTile(new PredictionTileKey(Level.OVERWORLD,x,0,lod),new int[1],new int[1],
                new ClientColumnSample[0],null,null,0,0,axis,spacing);
    }
    public static void verify()throws Exception{
        var defaults=new me.cortex.voxy.hxz.HxzSettings();
        if(!defaults.qualityFirstLod||defaults.smoothPredictionPreview)throw new AssertionError("Quality-first is not the default");
        defaults.profile("smooth");defaults.performanceProfile("low");
        if(!defaults.qualityFirstLod)throw new AssertionError("Preset silently disabled quality-first");
        defaults.qualityFirstLod=false;defaults.profile("balanced");defaults.performanceProfile("quality");
        if(defaults.qualityFirstLod)throw new AssertionError("Preset overwrote explicit opt-out");
        var type=Class.forName("dev.xantha.vss.client.prediction.PredictionTileManager$RenderSnapshot");
        var ctor=Arrays.stream(type.getDeclaredConstructors()).filter(c->c.getParameterCount()==6).findFirst().orElseThrow();ctor.setAccessible(true);
        var tilesMethod=type.getDeclaredMethod("tiles");tilesMethod.setAccessible(true);
        var covering=type.getDeclaredMethod("coveringTile",int.class,int.class,int.class);covering.setAccessible(true);
        var source=new LinkedHashMap<PredictionTileKey,PredictionTile>();
        for(var t:List.of(tile(0,4,64,16),tile(0,0,16,4),tile(1,0,64,1),tile(2,1,64,2),tile(6,0,32,2)))source.put(t.key(),t);
        Object snapshot=ctor.newInstance(Level.OVERWORLD,VssLodLayout.of(1024,1,true,true),source,Map.of(),null,Set.of());
        Map<?,?> accepted=(Map<?,?>)tilesMethod.invoke(snapshot);
        if(accepted.size()!=2)throw new AssertionError("Coarse/incomplete prediction reached the actual render snapshot: "+accepted.keySet());
        if(covering.invoke(snapshot,0,0,4)!=null)throw new AssertionError("Hidden parent still owns missing terrain");
        if(covering.invoke(snapshot,4,0,0)==null)throw new AssertionError("Fine prediction was lost");
        if(source.size()!=5)throw new AssertionError("Filtering destroyed reusable cache samples");
        var settings=me.cortex.voxy.hxz.HxzSettings.CONFIG;
        var enabled=settings.getClass().getField("qualityFirstLod");
        enabled.setBoolean(settings,false);
        try{
            Object original=ctor.newInstance(Level.OVERWORLD,VssLodLayout.of(1024,1,true,true),source,Map.of(),null,Set.of());
            if(((Map<?,?>)tilesMethod.invoke(original)).size()!=5)throw new AssertionError("Explicit opt-out failed");
        }finally{enabled.setBoolean(settings,true);}
        System.out.println("HXZ_QUALITY_FIRST PASS actual snapshot: rejects coarse and incomplete cached tiles, preserves fine tiles and source cache, no hidden ownership, opt-out");
    }
    public static String inspectRuntime()throws Exception{
        var level=net.minecraft.client.Minecraft.getInstance().level;
        var method=ClientPredictionState.class.getDeclaredMethod("renderSnapshot",net.minecraft.resources.ResourceKey.class);method.setAccessible(true);
        Object snapshot=method.invoke(null,level.dimension());
        if(snapshot==null)return "HXZ_QUALITY_RUNTIME pending";
        var tilesMethod=snapshot.getClass().getDeclaredMethod("tiles");tilesMethod.setAccessible(true);
        Map<?,?> accepted=(Map<?,?>)tilesMethod.invoke(snapshot);
        for(Object item:accepted.values()){
            var tile=(PredictionTile)item;
            if(tile.cellAxis()<64||tile.spacingBlocks()<1||tile.spacingBlocks()>2)
                throw new AssertionError("Actual RTF coarse tile reached renderer: "+tile.key());
        }
        long hidden=ClientPredictionState.readyTiles(level.dimension()).stream()
                .filter(t->t.cellAxis()<64||t.spacingBlocks()>2).count();
        return "HXZ_QUALITY_RUNTIME accepted="+accepted.size()+" coarseHidden="+hidden+" qualityFirst="+me.cortex.voxy.hxz.HxzSettings.CONFIG.qualityFirstLod;
    }
}
