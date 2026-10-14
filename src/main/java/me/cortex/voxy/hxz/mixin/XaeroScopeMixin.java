package me.cortex.voxy.hxz.mixin;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Map;
import java.util.Collections;
import java.util.WeakHashMap;
import dev.xantha.vss.api.VoxelColumnConsumer;
import me.cortex.voxy.hxz.HxzSettings;
import me.cortex.voxy.hxz.core.*;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(targets="dev.xantha.vss.compat.XaeroMapCompat",remap=false)
public class XaeroScopeMixin {
    @Shadow void onSessionEnd(){throw new AssertionError();}
    @Unique private final TaskEpoch hxz$epoch=new TaskEpoch();
    @Unique private final ThreadLocal<Long> hxz$producer=new ThreadLocal<>();
    // Weak keys also release tiles that the bounded upstream queue rejects or replaces.
    @Unique private final Map<Object,Long> hxz$owners=Collections.synchronizedMap(new WeakHashMap<>());
    @Unique private volatile boolean hxz$ready;
    @Unique private volatile Object hxz$level;
    @Unique private Object hxz$processor;
    @Unique private String hxz$map;
    @Unique private volatile long hxz$session=-1;
    @Unique private Object hxz$discarded;
    @Unique private Method hxz$getSession,hxz$getProcessor,hxz$getId;
    @Unique private boolean hxz$unavailable;

    @Inject(method={"pump","frameFlush"},at=@At("HEAD"))
    private void hxz$scope(CallbackInfo ci){
        if(!HxzSettings.CONFIG.sessionGuards)return;
        try{
            if(hxz$unavailable)return;
            if(hxz$getSession==null)hxz$getSession=Class.forName("xaero.map.WorldMapSession").getMethod("getCurrentSession");
            Object session=hxz$getSession.invoke(null),processor=null;String map=null;
            if(session!=null){
                if(hxz$getProcessor==null)hxz$getProcessor=session.getClass().getMethod("getMapProcessor");
                processor=hxz$getProcessor.invoke(session);
                if(processor!=null){
                    if(hxz$getId==null)hxz$getId=processor.getClass().getMethod("getCurrentWorldId");
                    map=(String)hxz$getId.invoke(processor);
                }
            }
            Object level=Minecraft.getInstance().level;long epoch=SessionNamespaces.epoch();
            if(level!=hxz$level||processor!=hxz$processor||!Objects.equals(map,hxz$map)||epoch!=hxz$session){
                hxz$ready=false;hxz$epoch.advance();onSessionEnd();
                hxz$level=level;hxz$processor=processor;hxz$map=map;hxz$session=epoch;
            }
            hxz$ready=level!=null&&processor!=null&&map!=null;
        }catch(ReflectiveOperationException e){
            hxz$ready=false;hxz$epoch.advance();onSessionEnd();hxz$unavailable=true;
            me.cortex.voxy.common.Logger.error("HXZ Xaero scope API unavailable; guarded map writes paused",e);
        }
    }
    @Inject(method="onSessionEnd",at=@At("HEAD"))
    private void hxz$invalidate(CallbackInfo ci){hxz$ready=false;hxz$epoch.advance();hxz$owners.clear();}
    @Inject(method="buildConsumer",at=@At("RETURN"),cancellable=true)
    private void hxz$consumer(CallbackInfoReturnable<VoxelColumnConsumer> ci){
        VoxelColumnConsumer delegate=ci.getReturnValue();
        ci.setReturnValue((level,dimension,x,z,column)->{
            if(!HxzSettings.CONFIG.sessionGuards){delegate.onVoxelColumnReceived(level,dimension,x,z,column);return;}
            long token=hxz$epoch.capture();
            if(!hxz$ready||level!=hxz$level||hxz$session!=SessionNamespaces.epoch())return;
            hxz$producer.set(token);
            try{delegate.onVoxelColumnReceived(level,dimension,x,z,column);}finally{hxz$producer.remove();}
        });
    }
    @Inject(method="offerPrepared",at=@At("HEAD"),cancellable=true)
    private void hxz$lateProducer(Object dimension,@Coerce Object tile,CallbackInfo ci){
        if(!HxzSettings.CONFIG.sessionGuards)return;
        Long token=hxz$producer.get();
        if(!hxz$ready||token==null||!hxz$epoch.current(token)||hxz$session!=SessionNamespaces.epoch()){ci.cancel();return;}
        hxz$owners.put(tile,token);
    }
    @Inject(method="commitEntry",at=@At("HEAD"),cancellable=true)
    private void hxz$beforeWrite(Object processor,Object dimension,@Coerce Object tile,CallbackInfoReturnable<Object> ci){
        if(!HxzSettings.CONFIG.sessionGuards)return;
        Long token=hxz$owners.get(tile);
        if(hxz$ready&&token!=null&&hxz$epoch.current(token)&&hxz$session==SessionNamespaces.epoch())return;
        // Acknowledge stale entries without writing pixels. FAILED would incorrectly trip
        // Xaero's error circuit; a deferral would retain this stale entry indefinitely.
        if(hxz$discarded==null)try{
            for(Object outcome:Class.forName("dev.xantha.vss.compat.XaeroMapCompat$Outcome").getEnumConstants())
                if(((Enum<?>)outcome).name().equals("COMMITTED")){hxz$discarded=outcome;break;}
        }catch(ClassNotFoundException e){throw new IllegalStateException(e);}
        ci.setReturnValue(hxz$discarded);
    }
}
