package hxz.smoke;

import java.nio.file.Path;
import java.lang.reflect.*;
import java.util.*;
import me.cortex.voxy.hxz.*;
import me.cortex.voxy.hxz.client.*;
import dev.xantha.vss.client.prediction.ClientTerrainSampler;
import dev.xantha.vss.networking.payloads.WorldgenProfileS2CPayload.DimensionProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class OptimizationProbe {
    public static void predictionCache(byte[] original,byte[] custom)throws Exception{
        var cfg=HxzSettings.CONFIG;boolean saved=cfg.predictionCacheIsolation,stable=cfg.stablePrediction,strict=cfg.predictionStrictSampling,features=cfg.predictionSurfaceFeatures;var namespace=HxzIdentity.clientNamespace;
        Class<?> type=Class.forName("dev.xantha.vss.client.prediction.PredictionCacheStorage");
        Method create=type.getDeclaredMethod("forWorld",Path.class,Path.class,String.class,String.class);create.setAccessible(true);
        Method directory=type.getDeclaredMethod("directory",ClientTerrainSampler.class);directory.setAccessible(true);
        try{
            cfg.predictionCacheIsolation=true;HxzIdentity.clientNamespace=()->"test-backend-a";
            Object a=create.invoke(null,Path.of("identity-fixture"),null,"same-seed-scope","localhost");
            var one=profile(original,7);var two=profile(custom,7);var registryChanged=profile(original,8);
            Path p1=(Path)directory.invoke(a,one),p2=(Path)directory.invoke(a,two),p3=(Path)directory.invoke(a,registryChanged);
            if(p1.equals(p2)||p1.equals(p3))throw new AssertionError("Changed RTF preset / registry reused prediction cache");
            if(!p1.equals(directory.invoke(a,profile(original,7))))throw new AssertionError("Identical settings do not reuse cache");
            cfg.stablePrediction=!stable;
            if(p1.equals(directory.invoke(a,one)))throw new AssertionError("Changed preview accuracy reused old cache");
            cfg.stablePrediction=stable;cfg.predictionStrictSampling=!strict;
            if(p1.equals(directory.invoke(a,one)))throw new AssertionError("Changed interpolation reused old cache");
            cfg.predictionStrictSampling=strict;
            cfg.predictionSurfaceFeatures=!features;
            if(p1.equals(directory.invoke(a,one)))throw new AssertionError("Changed surface extension reused old cache");
            cfg.predictionSurfaceFeatures=features;
            HxzIdentity.clientNamespace=()->"test-backend-b";
            Object b=create.invoke(null,Path.of("identity-fixture"),null,"same-seed-scope","localhost");
            if(p1.equals(directory.invoke(b,one)))throw new AssertionError("Backend reused another prediction cache");
            cfg.predictionCacheIsolation=false;
            if(!directory.invoke(a,one).equals(directory.invoke(a,two)))throw new AssertionError("Cache-isolation switch does not restore legacy behavior");
            System.out.println("HXZ_OPTIMIZATION PASS actual RTF preset bytes, registry identity and backend prediction-cache separation; identical reuse and opt-out");
        }finally{cfg.predictionCacheIsolation=saved;cfg.stablePrediction=stable;cfg.predictionStrictSampling=strict;cfg.predictionSurfaceFeatures=features;HxzIdentity.clientNamespace=namespace;}
    }
    private static ClientTerrainSampler profile(byte[] data,long fingerprint){
        var p=new DimensionProfile(ResourceLocation.parse("minecraft:overworld"),1234,-64,384,"reterraforged:generator","custom",fingerprint,0,data.length,data);
        return new ClientTerrainSampler(1234,p);
    }
    public static void run()throws Exception{
        predictionAdmission();
        var settings=HxzSettings.CONFIG;boolean original=settings.originalRendering,scheduling=settings.performanceScheduling;
        try{
            settings.originalRendering=true;settings.performanceScheduling=true;HxzClient.reset();
            if(HxzClient.initialDistance(16)!=1||HxzClient.flags()!=16)throw new AssertionError("NeoVoxy visual mode bypassed scheduling or retained HXZ visual flags");
            settings.performanceScheduling=false;
            if(HxzClient.initialDistance(16)!=16||HxzClient.flags()!=0)throw new AssertionError("Pure original opt-out failed");
            var sample=new HxzSettings();sample.originalRendering=true;sample.receiveServerLod=false;
            for(String name:List.of("low","balanced","quality")){
                sample.performanceProfile(name);
                if(!sample.originalRendering||sample.receiveServerLod||!sample.performanceScheduling)throw new AssertionError("Preset overwrote visual or sharing choice");
            }
            if(SodiumPanel.page("性能与加载")==null)throw new AssertionError("Common scheduling page missing");
            for(String name:List.of("low","balanced","quality"))if(net.caffeinemc.mods.sodium.client.config.ConfigManager.CONFIG.getOption(SodiumPanel.id("performance_"+name))==null)throw new AssertionError("Preset button missing");
        }finally{settings.originalRendering=original;settings.performanceScheduling=scheduling;HxzClient.reset();}
        var mc=Minecraft.getInstance();var listener=mc.getConnection();
        IPayloadContext active=(IPayloadContext)Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),new Class<?>[]{IPayloadContext.class},(p,m,a)->switch(m.getName()){case "connection"->listener.getConnection();case "listener"->listener;default->null;});
        IPayloadContext stale=(IPayloadContext)Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),new Class<?>[]{IPayloadContext.class},(p,m,a)->switch(m.getName()){case "connection"->listener.getConnection();default->null;});
        if(!HxzClientNetwork.isCurrent(active,false)||HxzClientNetwork.isCurrent(stale,false)||HxzClientNetwork.isCurrent(stale,true))throw new AssertionError("Stale packet listener accepted");
        if(dev.xantha.vss.api.VSSApi.dispatchColumnAndReport(mc.level,Level.NETHER,0,0,null))throw new AssertionError("Mismatched dimension dispatched");
        System.out.println("HXZ_OPTIMIZATION PASS independent renderer/scheduling switches, editable presets, stale connection and dimension rejection");
    }
    private static void predictionAdmission()throws Exception{
        var settings=HxzSettings.CONFIG;boolean scheduling=settings.performanceScheduling,shared=settings.sharedPredictionBudget;
        try{
            settings.performanceScheduling=true;settings.sharedPredictionBudget=true;HxzClient.reset();
            var governorField=HxzClient.class.getDeclaredField("governor");governorField.setAccessible(true);
            var governor=(me.cortex.voxy.hxz.core.FrameGovernor)governorField.get(null);
            governor.update(0,16,30,.95,0,5,.25,true,true);governor.update(100_000_000L,16,30,.95,0,5,.25,true,true);
            var frame=HxzClient.class.getDeclaredField("lastFrame");frame.setAccessible(true);frame.setLong(null,System.nanoTime());
            Class<?> type=Class.forName("dev.xantha.vss.client.prediction.PredictionCpuBudget");
            Method fixed=type.getDeclaredMethod("fixed",int.class);fixed.setAccessible(true);Object budget=fixed.invoke(null,2);
            for(String name:List.of("tryAcquire","tryReserve","tryReserveQueued")){
                boolean queued=name.equals("tryReserveQueued");Method m=queued?type.getDeclaredMethod(name,boolean.class,boolean.class,int.class):type.getDeclaredMethod(name,boolean.class,boolean.class);m.setAccessible(true);
                Object denied=queued?m.invoke(budget,false,false,2):m.invoke(budget,false,false);
                if(denied!=null)throw new AssertionError("Optional prediction admitted under foreground pressure: "+name);
                Object urgent=queued?m.invoke(budget,true,false,2):m.invoke(budget,true,false);
                if(urgent==null)throw new AssertionError("Urgent coverage starved: "+name);((AutoCloseable)urgent).close();
                settings.sharedPredictionBudget=false;
                Object allowed=queued?m.invoke(budget,false,false,2):m.invoke(budget,false,false);
                if(allowed==null)throw new AssertionError("Prediction-budget opt-out ignored: "+name);((AutoCloseable)allowed).close();
                settings.sharedPredictionBudget=true;
            }
            System.out.println("HXZ_OPTIMIZATION PASS real prediction lease/reservation deferral, urgent coverage, opt-out and capacity release");
        }finally{settings.performanceScheduling=scheduling;settings.sharedPredictionBudget=shared;HxzClient.reset();}
    }
}
