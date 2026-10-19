package dev.xantha.vss.client.prediction;

import dev.xantha.vss.client.prediction.PredictionTileManager.PredictionTile;
import dev.xantha.vss.client.prediction.PredictionTileManager.PredictionTileKey;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Real manager and adaptive budget; deterministic heap/clock, no game or worker threads. */
public final class PredictionPressureReclaimTest {
    static void set(Object o,String n,Object v)throws Exception {var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static Object get(Object o,String n)throws Exception {var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static Object call(Object o,String n,Class<?>[] ts,Object... args)throws Exception {var m=o.getClass().getDeclaredMethod(n,ts);m.setAccessible(true);return m.invoke(o,args);}
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void verify()throws Exception {
        for(int axis:new int[]{64,32,16})verifyAxis(axis);
    }
    private static void verifyAxis(int axis)throws Exception {
        var uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);
        var manager=(PredictionTileManager)((sun.misc.Unsafe)uf.get(null)).allocateInstance(PredictionTileManager.class);
        for(var f:PredictionTileManager.class.getDeclaredFields()) {
            if(Modifier.isStatic(f.getModifiers()))continue;f.setAccessible(true);
            if(f.getType()==Map.class)f.set(manager,new HashMap<>());
            if(f.getType()==java.util.concurrent.ConcurrentHashMap.class)f.set(manager,new java.util.concurrent.ConcurrentHashMap<>());
            if(f.getType()==Set.class)f.set(manager,new HashSet<>());
            if(f.getType()==AtomicLong.class)f.set(manager,new AtomicLong());
            if(f.getType()==AtomicInteger.class)f.set(manager,new AtomicInteger());
        }
        var dimension=ResourceKey.<Level>create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath("minecraft","overworld"));
        var layout=VssLodLayout.of(2048,1,true,true);
        var near=new PredictionTileKey(dimension,2,0,0);
        var far=new PredictionTileKey(dimension,100,0,0);
        var ready=new HashMap<PredictionTileKey,PredictionTile>();
        ready.put(near,new PredictionTile(near,new int[0],new int[0],null,null,null,0,0,32,2));
        int points=(axis+1)*(axis+1);
        var mesh=new PredictionMesh(new float[0],new float[0],new int[0],new float[0],new float[0],new int[0],new boolean[axis*axis],0,0,axis*axis);
        var obsolete=new PredictionTile(far,new int[points],new int[points],new ClientColumnSample[points],mesh,null,0,1,axis,64/axis);
        ready.put(far,obsolete);
        var residency=new PredictionRenderResidency();
        residency.retain(new PredictionTileManager.RenderSnapshot(dimension,layout,ready,Map.of(),Set.of(),Set.of()));
        residency.uploaded(obsolete);
        set(manager,"layout",layout);set(manager,"dimension",dimension);set(manager,"ready",ready);
        for(String n:List.of("idleTargets","transitionTargets","meshLimits","deferredUntil","failedAt","captureEpochs"))set(manager,n,new HashMap<>());
        for(String n:List.of("pending","surfaceDesired","surfaceReady","dirtyTiles"))set(manager,n,new HashSet<>());
        set(manager,"terrainLeaves",Set.of(near));set(manager,"terrainTargets",Map.of(near,64));
        set(manager,"ordinaryTargets",Map.of(near,64));set(manager,"desiredKeys",new HashSet<>(Set.of(near)));
        set(manager,"mediumCoverage",PredictionMediumCoverage.EMPTY);set(manager,"mediumSinceSurface",new AtomicInteger());
        set(manager,"meshRevision",new AtomicLong());
        long mib=PredictionMemoryBudget.MIB;
        var free=new AtomicLong(512*mib);var clock=new AtomicLong(1);var collections=new AtomicLong();
        var budget=PredictionMemoryBudget.adaptive(2048*mib,free::get,clock::get,1,collections::get);
        set(manager,"memoryBudget",budget);
        var reservation=budget.tryReserveBuild();check(reservation!=null,"fixture build reservation");
        long bytes=obsolete.retainedHeapBytes();check(reservation.retain(bytes),"fixture resident accounting");
        set(manager,"residentMemory",new HashMap<>(Map.of(far,reservation)));
        set(manager,"terrainColors",new PredictionTerrainColors());
        set(manager,"surfaceReady",new HashSet<>(Set.of(far)));set(manager,"storedTiles",new HashSet<>(Set.of(far)));
        free.set(2048*mib/10+128*mib-Math.min(256*1024,bytes/2)); // One real resident can fund the workspace deficit.
        check(budget.exhausted(),"fixture must deny a new workspace");
        check(!PredictionTileResidencyPolicy.coveredByAncestor(ready.keySet(),layout,far),"fixture must be parentless");
        check(PredictionTileResidencyPolicy.outOfRetirementRange(far,layout,0,0),"old detail must be outside retirement range");
        check(!PredictionTileResidencyPolicy.shouldRetirePinned(far,ready.keySet(),Set.of(near),layout,0,0),"baseline pins unneeded detail until a parent exists");
        for(int i=0;i<120;i++) {
            call(manager,"pruneReady",new Class<?>[]{int.class,int.class},0,0);
            if(!ready.containsKey(far))break;
            check((boolean)call(manager,"makeRoomForSurface",new Class<?>[]{int.class,int.class},0,0),"pressure path must consume planner tick");
            clock.addAndGet(1_000_000_000L);
        }
        check(!ready.containsKey(far),"120 seconds of sustained adaptive pressure: real manager retains out-of-range parentless cache (axis="+axis+") and consumes every planner tick; parent workspace cannot be admitted");
        check(ready.containsKey(near),"near selected quality was removed");
        check(budget.usedBytes()==0,"removed resident reservation was retained");
        check(manager.coverageFamilyEpoch(far)>0,"retirement did not invalidate GPU ownership");
        for(String name:List.of("surfaceReady","storedTiles"))check(!((Set<?>)get(manager,name)).contains(far),"retirement retained "+name);
        check(!((Map<?,?>)get(manager,"residentMemory")).containsKey(far),"retirement retained memory lease");
        residency.retain(new PredictionTileManager.RenderSnapshot(dimension,layout,ready,Map.of(),Set.of(),Set.of()));
        check(residency.resident(far)==null,"GPU residency retained obsolete parentless tile after ownership invalidation");
        free.addAndGet(bytes);collections.incrementAndGet();
        check(!budget.exhausted(),"collected obsolete cache did not restore workspace admission");
        check(!(boolean)call(manager,"makeRoomForSurface",new Class<?>[]{int.class,int.class},0,0),"recovered planner still exits before scheduling");
        boolean prior=me.cortex.voxy.hxz.HxzSettings.CONFIG.prioritizeDetailedPrediction;
        me.cortex.voxy.hxz.HxzSettings.CONFIG.prioritizeDetailedPrediction=false;
        try {check(!PredictionPressureRetirement.coveredOrObsolete(ready.keySet(),layout,far,Map.of(far,obsolete),Set.of(),Set.of(),0,0),"opt-out changed original policy");}
        finally {me.cortex.voxy.hxz.HxzSettings.CONFIG.prioritizeDetailedPrediction=prior;}
        check(!PredictionPressureRetirement.coveredOrObsolete(Set.of(far),layout,far,Map.of(far,obsolete),Set.of(far),Set.of(),0,0),"desired detail was made evictable");
        check(!PredictionPressureRetirement.coveredOrObsolete(Set.of(far),layout,far,Map.of(far,obsolete),Set.of(),Set.of(far),0,0),"pending detail was made evictable");
        check(!PredictionPressureRetirement.coveredOrObsolete(Set.of(far),layout,far,Map.of(far,obsolete),Set.of(),Set.of(),6400,0),"visible parentless detail was made evictable");
        System.out.println("HXZ_PRESSURE PASS axis="+axis+" real adaptive budget/manager: obsolete parentless detail releases ownership and reservation, near detail survives, collection restores scheduling; opt-out and desired/pending/horizon guards");
    }
    public static void main(String[] args)throws Exception {verify();}
}
