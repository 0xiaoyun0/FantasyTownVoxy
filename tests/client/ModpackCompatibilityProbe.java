package hxz.smoke;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import me.cortex.voxy.hxz.HxzSettings;
import me.cortex.voxy.hxz.compat.SablePlotGuard;
import me.cortex.voxy.hxz.core.TaskEpoch;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/** Real installed Sable / Xaero API checks, exclusively inside an isolated integrated world. */
public final class ModpackCompatibilityProbe {
    private static Object field(Object object,String name)throws Exception{
        Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private static Object staticField(Class<?> type,String name)throws Exception{
        Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);
    }
    private static Method method(Class<?> type,String name){
        for(Method m:type.getDeclaredMethods())if(m.getName().equals(name)){m.setAccessible(true);return m;}
        throw new AssertionError(name);
    }
    private static int[] plot(Level level)throws Exception{
        Class<?> type=Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
        Object container=type.getMethod("getContainer",Level.class).invoke(null,level);
        if(container==null)throw new AssertionError("No Sable level container");
        Object origin=type.getMethod("getOrigin").invoke(container);
        int shift=(int)type.getMethod("getLogPlotSize").invoke(container);
        return new int[]{origin.getClass().getField("x").getInt(origin)<<shift,origin.getClass().getField("y").getInt(origin)<<shift};
    }
    public static void run()throws Exception{
        var mc=Minecraft.getInstance();int[] pos=plot(mc.level);
        boolean saved=HxzSettings.CONFIG.sableCompatibility;
        try{
            HxzSettings.CONFIG.sableCompatibility=true;
            if(!SablePlotGuard.excludes(mc.level,pos[0],pos[1])||SablePlotGuard.excludes(mc.level,0,0))throw new AssertionError("Actual Sable plot bounds incorrect");
            var chunk=new net.minecraft.world.level.chunk.LevelChunk(mc.level,new net.minecraft.world.level.ChunkPos(pos[0],pos[1]));
            if(me.cortex.voxy.common.world.service.VoxelIngestService.tryIngestChunk(null,chunk))throw new AssertionError("Plot created static Voxy engine");
            if(dev.xantha.vss.api.VSSApi.dispatchColumnAndReport(mc.level,mc.level.dimension(),pos[0],pos[1],null))throw new AssertionError("Plot reached VSS terrain consumers");
            HxzSettings.CONFIG.sableCompatibility=false;
            if(SablePlotGuard.excludes(mc.level,pos[0],pos[1]))throw new AssertionError("Client plot toggle ignored");
        }finally{HxzSettings.CONFIG.sableCompatibility=saved;}
        mc.getSingleplayerServer().submit(()->{
            boolean previous=me.cortex.voxy.hxz.server.ServerSafetyPolicy.filterSablePlots;
            try{
                var level=mc.getSingleplayerServer().overworld();int[] p=plot(level);
                me.cortex.voxy.hxz.server.ServerSafetyPolicy.filterSablePlots=true;
                if(!SablePlotGuard.excludes(level,p[0],p[1]))throw new AssertionError("Server Sable filter unavailable");
                me.cortex.voxy.hxz.server.ServerSafetyPolicy.filterSablePlots=false;
                if(SablePlotGuard.excludes(level,p[0],p[1]))throw new AssertionError("Server plot toggle ignored");
            }catch(Exception e){throw new RuntimeException(e);}
            finally{me.cortex.voxy.hxz.server.ServerSafetyPolicy.filterSablePlots=previous;}
        }).get(10,TimeUnit.SECONDS);
        System.out.println("HXZ_COMPAT PASS Sable 2.0.3 real client/server plot bounds, static-ingest and VSS rejection, independent opt-outs");
        xaeroRace();
    }
    @SuppressWarnings("unchecked")
    private static void xaeroRace()throws Exception{
        Class<?> type=Class.forName("dev.xantha.vss.compat.XaeroMapCompat");Object active=staticField(type,"instance");
        if(active==null)throw new AssertionError("Actual Xaero bridge did not initialize");
        var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
        Object bridge=ctor.newInstance(field(active,"h"),staticField(type,"PRODUCTION_LEVEL_OPS"),(BooleanSupplier)()->false,(BooleanSupplier)()->true,(Consumer<Object>)x->{},(Consumer<Object>)x->{});
        Method pump=method(type,"pump"),offer=method(type,"offerPrepared"),end=method(type,"onSessionEnd"),commit=method(type,"commitEntry");
        pump.invoke(bridge);
        if(!(boolean)field(bridge,"hxz$ready"))throw new AssertionError("Actual Xaero world identity not available");
        Class<?> tileType=offer.getParameterTypes()[1];var tileCtor=tileType.getDeclaredConstructors()[0];tileCtor.setAccessible(true);
        Object[] args=new Object[tileCtor.getParameterCount()];args[0]=1000;args[1]=1000;args[2]=-64;
        for(int i=3;i<args.length;i++)args[i]=Array.newInstance(tileCtor.getParameterTypes()[i].componentType(),256);
        Object tile=tileCtor.newInstance(args);
        Object lock=field(bridge,"queueLock"),dimension=Minecraft.getInstance().level.dimension();
        TaskEpoch epoch=(TaskEpoch)field(bridge,"hxz$epoch");long token=epoch.capture();
        ThreadLocal<Long> producer=(ThreadLocal<Long>)field(bridge,"hxz$producer");AtomicReference<Throwable> failure=new AtomicReference<>();
        Thread worker=new Thread(()->{
            producer.set(token);
            try{offer.invoke(bridge,dimension,tile);}catch(Throwable e){failure.set(e);}finally{producer.remove();}
        },"HXZ test late Xaero producer");
        synchronized(lock){
            worker.start();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(worker.getState()!=Thread.State.BLOCKED&&System.nanoTime()<deadline)Thread.sleep(1);
            if(worker.getState()!=Thread.State.BLOCKED)throw new AssertionError("Producer did not reach upstream queue lock");
            end.invoke(bridge);pump.invoke(bridge);
            if(epoch.current(token))throw new AssertionError("Map epoch not invalidated");
        }
        worker.join(5000);if(worker.isAlive()||failure.get()!=null)throw new AssertionError("Producer failure",failure.get());
        if(((Map<?,?>)field(bridge,"queue")).size()!=1)throw new AssertionError("Late insertion race was not reproduced");
        // A sentinel processor would fail in Xaero's pixel writer; stale work must be acknowledged before that point.
        Object outcome=commit.invoke(bridge,new Object(),dimension,tile);
        if(!((Enum<?>)outcome).name().equals("COMMITTED")||((AtomicLong)field(bridge,"commitFailures")).get()!=0)throw new AssertionError("Stale map work reached pixel writer or poisoned error circuit");
        System.out.println("HXZ_COMPAT PASS Xaero real map identity and concurrent late-producer race: old tile discarded before pixel write");
    }
}
