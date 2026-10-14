package hxz.safetyprobe;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.commands.Commands;

/** Isolated fixture only. Counts actual VSS column handlers per server tick. */
@Mod("hxz_safety_probe")
public final class SafetyProbe {
    private static boolean armed;
    private static int current,max,total,ticks,invalid;
    private static int recoveryTicks;
    public static void column(int id){if(armed){current++;if(id>=2_000_000 && id<2_000_010)invalid++;}}
    public SafetyProbe(){
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class,event->{
            var root=Commands.literal("hxzsafetytest");
            root.then(Commands.literal("arm").executes(c->{armed=true;current=max=total=ticks=invalid=0;System.out.println("HXZ_FLOOD armed");return 1;}));
            root.then(Commands.literal("finish").executes(c->{
                boolean pass=max<=4 && total>0 && ticks>=60 && invalid==0;
                System.out.println("HXZ_FLOOD "+(pass?"PASS":"FAIL")+" maxColumnsPerTick="+max+" total="+total+" ticks="+ticks+" invalidDispatched="+invalid);
                try{System.out.println(Class.forName("me.cortex.voxy.hxz.server.ServerTickGuard").getMethod("status").invoke(null));}catch(ReflectiveOperationException ignored){}
                if(pass){
                    try{
                        var guard=Class.forName("me.cortex.voxy.hxz.server.ServerTickGuard");
                        guard.getMethod("run",String.class,Runnable.class).invoke(null,"test injection",(Runnable)()->{throw new IllegalStateException("HXZ_TEST expected failure");});
                        if(!(Boolean)guard.getMethod("circuitOpen").invoke(null))throw new AssertionError("Failure did not pause LOD");
                        recoveryTicks=1;
                    }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
                }
                armed=false;return 1;
            }));event.getDispatcher().register(root);
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,event->{
            if(armed){max=Math.max(max,current);total+=current;current=0;ticks++;}
            if(recoveryTicks>0 && ++recoveryTicks==4){
                try{
                    var server=event.getServer();var dispatcher=server.getCommands().getDispatcher();var source=server.createCommandSourceStack();
                    dispatcher.execute("voxyhxzserver safety reset",source);
                    var guard=Class.forName("me.cortex.voxy.hxz.server.ServerTickGuard");
                    if((Boolean)guard.getMethod("circuitOpen").invoke(null))throw new AssertionError("Reset did not recover");
                    dispatcher.execute("voxyhxzserver safety set columnsPerTick 8",source);
                    var policy=Class.forName("me.cortex.voxy.hxz.server.ServerSafetyPolicy");Object limits=policy.getField("limits").get(null);
                    if((Integer)limits.getClass().getMethod("columnsPerTick").invoke(limits)!=8)throw new AssertionError("Admin setting not applied");
                    dispatcher.execute("voxyhxzserver safety set columnsPerTick 16",source);
                    var count=new java.util.concurrent.atomic.AtomicInteger();var discard=new java.util.concurrent.atomic.AtomicInteger();
                    var complete=guard.getMethod("complete",net.minecraft.server.MinecraftServer.class,Runnable.class,Runnable.class);
                    for(int i=0;i<128;i++)complete.invoke(null,server,(Runnable)count::incrementAndGet,(Runnable)discard::incrementAndGet);
                    if(count.get()!=0 || discard.get()<96)throw new AssertionError("Completion burst was not queued/bounded");
                    var networking=Class.forName("dev.xantha.vss.networking.server.VSSServerNetworking");
                    var disk=networking.getDeclaredField("DISK_RUNTIME");disk.setAccessible(true);Object runtime=disk.get(null);
                    Object snapshot=runtime.getClass().getMethod("snapshot").invoke(runtime);
                    if((Integer)snapshot.getClass().getMethod("readThreads").invoke(snapshot)>2)throw new AssertionError("Old worker setting bypassed cap");
                    var tickStart=guard.getDeclaredField("tickStart");tickStart.setAccessible(true);
                    for(int i=0;i<3;i++){tickStart.setLong(null,System.nanoTime()-200_000_000L);guard.getMethod("begin",net.minecraft.server.MinecraftServer.class).invoke(null,server);}
                    if((Boolean)guard.getMethod("accepting").invoke(null))throw new AssertionError("Slow ticks did not pause admission");
                    dispatcher.execute("voxyhxzserver safety reset",source);
                    System.out.println("HXZ_RECOVERY PASS exception isolated, server tick survived, OP command reset/persist, bounded completion burst");
                    System.out.println("HXZ_PRESSURE PASS legacy thread cap and injected slow-tick pause");
                }catch(Exception e){throw new RuntimeException(e);}
            }
        });
    }
}
