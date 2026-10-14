package me.cortex.voxy.hxz.compat;

import java.lang.reflect.Method;
import me.cortex.voxy.hxz.HxzSettings;
import me.cortex.voxy.hxz.platform.HxzPlatform;
import me.cortex.voxy.hxz.server.ServerSafetyPolicy;
import net.minecraft.world.level.Level;

/** Optional companion API: bounds checks only, never loads a chunk or creates a ship ticket. */
public final class SablePlotGuard {
    private record Api(Object instance,Method inPlotGrid) {}
    private static volatile Api api;
    private static volatile boolean resolved;
    private static boolean warned;
    private static synchronized void resolve(){
        if(resolved)return;
        try{
            if(HxzPlatform.getInstance().isModLoaded("sable")){
                Class<?> type=Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
                api=new Api(type.getField("INSTANCE").get(null),type.getMethod("isInPlotGrid",Level.class,int.class,int.class));
            }
        }catch(ReflectiveOperationException|LinkageError e){disable(e);}
        finally{resolved=true;}
    }
    private static synchronized void disable(Throwable e){
        api=null;
        if(!warned){warned=true;dev.xantha.vss.common.VSSLogger.warn("HXZ Sable plot API unavailable; plot adapter disabled",e);}
    }
    public static boolean excludes(Level level,int chunkX,int chunkZ){
        if(level==null || !(level.isClientSide()?HxzSettings.CONFIG.sableCompatibility:ServerSafetyPolicy.filterSablePlots))return false;
        if(!resolved)resolve();
        Api current=api;if(current==null)return false;
        try{return (boolean)current.inPlotGrid.invoke(current.instance,level,chunkX,chunkZ);}
        catch(ReflectiveOperationException|LinkageError e){disable(e);return false;}
    }
    private SablePlotGuard(){}
}
