package me.cortex.voxy.hxz.server;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import me.cortex.voxy.hxz.platform.HxzPlatform;

public final class ServerSafetyConfig {
    public record Option(String key,int min,int max,boolean bool) {}
    public static final List<Option> OPTIONS=List.of(
        b("enabled"),b("limitRequests"),b("tickBudget"),b("pauseOnOverload"),b("limitResources"),
        b("isolateErrors"),b("allowLodGeneration"),b("backgroundPreload"),b("filterSablePlots"),b("cacheProfileTransfers"),n("profileCacheMiB",4,64),
        b("downloadScheduleEnabled"),n("downloadBlockStartHour",0,23),n("downloadBlockStartMinute",0,59),
        n("downloadBlockEndHour",0,23),n("downloadBlockEndMinute",0,59),n("downloadUtcOffsetMinutes",-720,840),
        b("downloadLimitPerPlayer"),n("downloadPlayerBytesPerSecond",1024,12500000),
        n("columnsPerTick",1,128),n("columnsPerPlayerTick",1,32),n("queuedColumns",16,4096),
        n("queuedColumnsPerPlayer",8,512),n("tickBudgetMillis",1,20),n("pauseAboveMillis",50,1000),
        n("heapPercent",50,95),n("cooldownSeconds",1,60),n("diskTasks",4,256),n("workerThreads",1,8),
        n("completionTasks",4,128),n("sendPackets",1,32),n("sendQueueMiB",1,64),n("cacheMiB",4,256),n("packingMiB",1,64));
    private static Option b(String key){return new Option(key,0,1,true);}
    private static Option n(String key,int min,int max){return new Option(key,min,max,false);}
    public boolean enabled=true,limitRequests=true,tickBudget=true,pauseOnOverload=true,limitResources=true,
        isolateErrors=true,allowLodGeneration=false,backgroundPreload=false,filterSablePlots=true,cacheProfileTransfers=true;
    public int profileCacheMiB=16;
    public boolean downloadScheduleEnabled=false,downloadLimitPerPlayer=true;
    public int downloadBlockStartHour=17,downloadBlockStartMinute=0,downloadBlockEndHour=23,downloadBlockEndMinute=0,
        downloadUtcOffsetMinutes=480,downloadPlayerBytesPerSecond=131072;
    public int columnsPerTick=16,columnsPerPlayerTick=4,queuedColumns=512,queuedColumnsPerPlayer=64,
        tickBudgetMillis=4,pauseAboveMillis=80,heapPercent=85,cooldownSeconds=5,diskTasks=64,
        workerThreads=2,completionTasks=32,sendPackets=8,sendQueueMiB=8,cacheMiB=32,packingMiB=8;
    public static final ServerSafetyConfig CONFIG=load();
    private static Path path(){return HxzPlatform.getInstance().getConfigDir().resolve("voxy-hxz-server.json");}
    private static ServerSafetyConfig load(){
        ServerSafetyConfig c=new ServerSafetyConfig();
        try{if(Files.exists(path())){var value=new GsonBuilder().create().fromJson(Files.readString(path()),ServerSafetyConfig.class);if(value!=null)c=value;}}
        catch(Exception e){dev.xantha.vss.common.VSSLogger.warn("HXZ server safety config invalid; using safe defaults",e);}
        c.publish();return c;
    }
    public void publish(){
        ServerSafetyPolicy.filterSablePlots=filterSablePlots;
        for(var o:OPTIONS)if(!o.bool())try{var f=getClass().getField(o.key());f.setInt(this,Math.clamp(f.getInt(this),o.min(),o.max()));}
            catch(ReflectiveOperationException e){throw new AssertionError(e);}
        ServerDownloads.configure(this);
        ServerSafetyPolicy.profileTransfers=new ServerSafetyPolicy.ProfileTransfers(cacheProfileTransfers,profileCacheMiB);
        ServerSafetyPolicy.limits=new ServerSafetyPolicy.Limits(enabled,limitRequests,tickBudget,pauseOnOverload,
            limitResources,isolateErrors,allowLodGeneration,backgroundPreload,columnsPerTick,columnsPerPlayerTick,
            queuedColumns,queuedColumnsPerPlayer,tickBudgetMillis,pauseAboveMillis,heapPercent,cooldownSeconds,
            diskTasks,workerThreads,completionTasks,sendPackets,sendQueueMiB,cacheMiB,packingMiB);
    }
    public Object get(String key){try{return getClass().getField(key).get(this);}catch(ReflectiveOperationException e){throw new IllegalArgumentException(key,e);}}
    public void set(String key,Object value){try{getClass().getField(key).set(this,value);}catch(ReflectiveOperationException e){throw new IllegalArgumentException(key,e);}save();}
    public void save(){publish();try{me.cortex.voxy.hxz.config.ConfigIO.write(path(),new GsonBuilder().setPrettyPrinting().create().toJson(this));}
        catch(java.io.IOException e){throw new IllegalStateException("Cannot save server safety config",e);}}
    public static void init(){CONFIG.publish();if(!Files.exists(path()))CONFIG.save();}
}
