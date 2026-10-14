package me.cortex.voxy.client.config;

import com.google.gson.*;
import java.nio.file.*;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.ModLoaderUtil;
import me.cortex.voxy.common.util.cpu.CpuLayout;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.hxz.config.ConfigIO;

public class VoxyConfig {
    private static final Gson GSON=new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).setPrettyPrinting().create();
    public static VoxyConfig CONFIG=loadOrCreate();
    public boolean enabled=true,enableRendering=true,ingestEnabled=true;
    public int sectionRenderDistance=16,serviceThreads=Math.max(1,(int)(CpuLayout.getCoreCount()/1.5));
    public float subDivisionSize=64;
    public boolean renderVanillaFog=false,renderStatistics=false,dontUseSodiumBuilderThreads=false;
    private static Path getConfigPath(){return ModLoaderUtil.getConfigDir().resolve("voxy-config.json");}
    private static VoxyConfig loadOrCreate(){
        VoxyConfig value=new VoxyConfig();
        if(!VoxyCommon.isAvailable()){value.enabled=value.enableRendering=false;return value;}
        if(Files.exists(getConfigPath())){
            try{value=ConfigIO.read(getConfigPath(),VoxyConfig.class,GSON);value.validate();}
            catch(Exception e){Logger.error("Invalid Voxy config; keeping the file and using safe defaults for this session",e);}
        }else value.save();
        return value;
    }
    public void validate(){sectionRenderDistance=Math.clamp(sectionRenderDistance,2,64);serviceThreads=Math.clamp(serviceThreads,1,Math.max(1,CpuLayout.getCoreCount()));subDivisionSize=Float.isFinite(subDivisionSize)?Math.clamp(subDivisionSize,28,256):64;}
    public void save(){validate();try{ConfigIO.write(getConfigPath(),GSON.toJson(this));}catch(Exception e){Logger.error("Failed to save Voxy config",e);}}
    public boolean isRenderingEnabled(){return VoxyCommon.isAvailable()&&enabled&&enableRendering;}
}
