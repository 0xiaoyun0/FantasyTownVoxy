package me.cortex.voxy.hxz.client;

import java.nio.file.*;
import com.google.gson.*;
import me.cortex.voxy.hxz.HxzSettings;
import me.cortex.voxy.hxz.config.ConfigIO;
import me.cortex.voxy.hxz.platform.HxzPlatform;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.commonImpl.VoxyCommon;
import dev.xantha.vss.config.VSSClientConfig;
import dev.xantha.vss.networking.client.VSSClientNetworking;
import net.minecraft.client.Minecraft;

public final class LiveClientConfig {
    private static boolean pending,rebuild;
    private static long applyAfter;
    public static String message="设置应用后即时更新；重建远景时可能短暂停顿。";
    private static final Gson VOXY_GSON=new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).setPrettyPrinting().create();
    public static void changed(boolean needsRebuild){pending=true;rebuild|=needsRebuild;applyAfter=System.nanoTime()+300_000_000L;}
    public static void save(){
        try{
            HxzSettings.CONFIG.save();VoxyConfig.CONFIG.save();VSSClientConfig.CONFIG.normalizeAndSave();
            applyAfter=System.nanoTime()+300_000_000L;
        }catch(RuntimeException e){message="保存失败："+e.getMessage();me.cortex.voxy.common.Logger.error(message,e);}
    }
    public static void reload(){
        try{
            Path dir=HxzPlatform.getInstance().getConfigDir();
            var hxz=ConfigIO.read(dir.resolve("voxy-hxz-neovoxy.json"),HxzSettings.class,ConfigIO.GSON);hxz.validate();
            var voxy=ConfigIO.read(dir.resolve("voxy-config.json"),VoxyConfig.class,VOXY_GSON);
            var vss=ConfigIO.read(dir.resolve("vss-client-config.json"),VSSClientConfig.class,ConfigIO.GSON);
            validateVoxy(voxy);
            var validate=VSSClientConfig.class.getDeclaredMethod("validate");validate.setAccessible(true);validate.invoke(vss);
            ConfigIO.copy(hxz,HxzSettings.CONFIG);ConfigIO.copy(voxy,VoxyConfig.CONFIG);ConfigIO.copy(vss,VSSClientConfig.CONFIG);
            HxzSettings.CONFIG.receiveServerLod=vss.receiveServerLods;
            changed(true);message="已读取配置；正在应用。";
        }catch(Exception e){message="重载失败，继续使用原设置："+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
    }
    private static void validateVoxy(VoxyConfig c){
        c.sectionRenderDistance=Math.clamp(c.sectionRenderDistance,2,64);c.serviceThreads=Math.clamp(c.serviceThreads,1,Math.max(1,Runtime.getRuntime().availableProcessors()));
        c.subDivisionSize=Float.isFinite(c.subDivisionSize)?Math.clamp(c.subDivisionSize,28,256):64;
    }
    public static void tick(){
        me.cortex.voxy.hxz.importer.LodImportJob.tick();
        if(!pending||System.nanoTime()<applyAfter)return;
        if(rebuild&&me.cortex.voxy.hxz.importer.LodImportJob.active!=null){message="导入完成后应用需要重建的设置；可在导入页取消任务。";return;}
        pending=false;boolean recreate=rebuild;rebuild=false;
        try{
            var mc=Minecraft.getInstance();validateVoxy(VoxyConfig.CONFIG);HxzClient.reset();
            if(mc.level!=null){
                if(recreate){
                    VSSClientNetworking.stopClientSessionForWorldShutdown();
                    if(mc.levelRenderer instanceof IGetVoxyRenderSystem renderer)renderer.shutdownRenderer();
                    VoxyCommon.shutdownInstance();
                    if(VoxyConfig.CONFIG.enabled&&VoxyCommon.isAvailable()){
                        VoxyCommon.createInstance();
                        if(VoxyConfig.CONFIG.enableRendering&&mc.levelRenderer instanceof IGetVoxyRenderSystem renderer)renderer.createRenderer();
                    }
                }else{
                    var instance=VoxyCommon.getInstance();if(instance!=null)instance.updateDedicatedThreads();
                    if(mc.levelRenderer instanceof IGetVoxyRenderSystem renderer&&renderer.getVoxyRenderSystem()!=null)renderer.getVoxyRenderSystem().setRenderDistance(VoxyConfig.CONFIG.sectionRenderDistance);
                    if(!VSSClientConfig.CONFIG.receiveServerLods)VSSClientNetworking.stopClientSessionForWorldShutdown();
                }
                VSSClientNetworking.sendBandwidthPreference();
            }
            me.cortex.voxy.client.RenderStatistics.enabled=VoxyConfig.CONFIG.renderStatistics;
            message="已应用并保存。";
        }catch(RuntimeException e){message="远景更新失败，已暂停远景显示："+e.getClass().getSimpleName();VoxyConfig.CONFIG.enableRendering=false;me.cortex.voxy.common.Logger.error(message,e);}
    }
}
