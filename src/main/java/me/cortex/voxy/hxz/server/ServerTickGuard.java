package me.cortex.voxy.hxz.server;

import dev.xantha.vss.common.VSSLogger;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Cooperative budgets: a running Minecraft operation cannot be forcibly interrupted. */
public final class ServerTickGuard {
    private record Completion(Runnable work,Runnable discard,long epoch,long created) {}
    private static final ArrayDeque<Completion> completions=new ArrayDeque<>();
    private static volatile long epoch;
    private static volatile boolean running,paused,circuitOpen;
    private static long tickStart,deadline,pausedUntil;
    private static int slowTicks;
    private static double averageTickMillis;
    private static double lastTickMillis;
    private static long completed,rejected,errors,maxWorkNanos;
    private static String lastFailure="none";

    public static void preTick(ServerTickEvent.Pre event){tickStart=System.nanoTime();}
    public static void postTick(ServerTickEvent.Post event){if(tickStart!=0)lastTickMillis=(System.nanoTime()-tickStart)/1_000_000.0;}
    public static void start(){stop();running=true;paused=false;circuitOpen=false;slowTicks=0;averageTickMillis=0;lastTickMillis=0;pausedUntil=0;}
    public static void stop(){running=false;epoch++;ServerIngress.clear();clearCompletions();}
    private static void clearCompletions(){
        List<Completion> discard;
        synchronized(completions){discard=new ArrayList<>(completions);completions.clear();}
        for(var c:discard)c.discard().run();
    }
    public static void complete(MinecraftServer server,Runnable task,Runnable discard){
        var p=ServerSafetyPolicy.limits;
        if(!p.enabled()){server.execute(task);return;}
        synchronized(completions){
            if(running && !circuitOpen && completions.size()<p.completionTasks()){
                completions.addLast(new Completion(task,discard,epoch,System.nanoTime()));return;
            }
            rejected++;
        }
        discard.run();
    }
    public static void begin(MinecraftServer server){
        var p=ServerSafetyPolicy.limits;long now=System.nanoTime();
        double duration=Math.max(lastTickMillis,tickStart==0?0:(now-tickStart)/1_000_000.0);
        averageTickMillis=averageTickMillis==0?duration:averageTickMillis*0.9+duration*0.1;
        var vm=Runtime.getRuntime();long used=vm.totalMemory()-vm.freeMemory();
        boolean stressed=duration>p.pauseAboveMillis() || used>vm.maxMemory()*p.heapPercent()/100;
        slowTicks=stressed?slowTicks+1:0;
        if(p.enabled() && p.pauseOnOverload() && slowTicks>=3)
            pausedUntil=now+p.cooldownSeconds()*1_000_000_000L;
        boolean next=p.enabled() && p.pauseOnOverload() && now<pausedUntil;
        if(next!=paused){paused=next;VSSLogger.info("HXZ LOD admission "+(next?"paused for server load":"resumed"));}
        deadline=p.enabled() && p.tickBudget()?now+p.tickBudgetMillis()*1_000_000L:Long.MAX_VALUE;
        if(circuitOpen && p.enabled())return;
        // Completion and new work alternate to avoid either queue starving the other.
        if((server.getTickCount()&1)==0)drainCompletions(8);
        ServerIngress.drain(server,p.enabled() && p.tickBudget()?Math.min(deadline,now+Math.max(1,p.tickBudgetMillis()/2)*1_000_000L):Long.MAX_VALUE);
        drainCompletions(8);
    }
    private static void drainCompletions(int count){
        for(int i=0;i<count && available();i++){
            Completion c;synchronized(completions){c=completions.pollFirst();}
            if(c==null)return;
            if(c.epoch()!=epoch || System.nanoTime()-c.created()>10_000_000_000L){c.discard().run();continue;}
            run("disk completion",()->{try{c.work().run();}catch(RuntimeException e){c.discard().run();throw e;}});completed++;
        }
    }
    public static boolean available(){return !ServerSafetyPolicy.limits.enabled() || !circuitOpen && System.nanoTime()<deadline;}
    public static boolean accepting(){return running && (!ServerSafetyPolicy.limits.enabled() || !paused && !circuitOpen);}
    public static boolean circuitOpen(){return circuitOpen && ServerSafetyPolicy.limits.enabled();}
    public static boolean background(){var p=ServerSafetyPolicy.limits;return !p.enabled() || p.backgroundPreload() && accepting();}
    public static void run(String label,Runnable action){
        long before=System.nanoTime();
        try{action.run();}
        catch(RuntimeException e){
            var p=ServerSafetyPolicy.limits;
            if(!p.enabled() || !p.isolateErrors())throw e;
            errors++;lastFailure=label+": "+e.getClass().getSimpleName();circuitOpen=true;
            ServerIngress.clear();clearCompletions();
            VSSLogger.warn("HXZ paused LOD services after "+lastFailure+"; inspect logs, then /voxyhxzserver safety reset",e);
        }
        finally{maxWorkNanos=Math.max(maxWorkNanos,System.nanoTime()-before);}
    }
    public static void reset(){circuitOpen=false;lastFailure="none";paused=false;pausedUntil=0;slowTicks=0;}
    public static long generationDeadline(){return ServerSafetyPolicy.limits.enabled() && ServerSafetyPolicy.limits.tickBudget()
        ? Math.min(deadline,System.nanoTime()+1_000_000L):Long.MAX_VALUE;}
    public static String panelStatus(){return String.format(Locale.ROOT,"%s；等待 %d 列；近期 tick %.1f 毫秒",
        circuitOpen()?"共享异常后已暂停":paused?"服务器繁忙，暂缓新请求":"服务正常",ServerIngress.queued(),averageTickMillis);}
    public static String status(){int pending;synchronized(completions){pending=completions.size();}
        return String.format(Locale.ROOT,"safety=%s, paused=%s, circuit=%s, queued=%d, completions=%d, accepted=%d, rejected=%d, finished=%d, errors=%d, observedTick=%.1fms, maxLodStep=%.1fms, last=%s",
            ServerSafetyPolicy.limits.enabled(),paused,circuitOpen,ServerIngress.queued(),pending,ServerIngress.accepted(),ServerIngress.rejected()+rejected,completed,errors,averageTickMillis,maxWorkNanos/1_000_000.0,lastFailure);}
}
