package me.cortex.voxy.hxz;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import me.cortex.voxy.hxz.platform.HxzPlatform;

/** A single option registry drives JSON, commands and the Sodium settings page. */
public final class HxzSettings {
    public record Option(String key,String label,String help,int min,int max,boolean restart) {}
    public static final List<Option> OPTIONS=List.of(
        new Option("originalRendering","原版 NeoVoxy 渲染","选择 NeoVoxy 画面；性能调度可独立开启。完全恢复原版行为可使用原版预设。",0,1,true),
        new Option("performanceScheduling","性能调度总开关","两种画面模式均可使用；关闭后绕过渐进加载、动态视距及 HXZ 资源预算。",0,1,true),
        new Option("sharedPredictionBudget","预测跟随负载","本模组帧耗时或内存紧张时暂缓非紧急预测，给当前画面留出资源。",0,1,false),
        new Option("sessionGuards","转服与地图任务校验","阻止旧连接数据和旧地图后台任务写入当前世界。",0,1,true),
        new Option("xaeroMapIsolation","Xaero 小地图与世界地图隔离","按完整服务器地址（含端口）、后端身份和世界种子隔离地图与路标。重新进服生效；旧地图保留，关闭后可回到旧目录。无身份的同地址同种子后端须填写手动世界标识。",0,1,false),
        new Option("predictionCacheIsolation","按地形预设隔离预测缓存","RTF 预设、生成器或注册表变化后使用独立预测缓存；保留旧文件和真实 LOD。",0,1,true),
        new Option("stablePrediction","稳定地形预测","低模与精修使用相同的地表和水面采样；RTF 预览保留地形滤波。初次生成较慢，仍受后台预算限制。关闭恢复 VSS 快速预览。",0,1,true),
        new Option("predictionStrictSampling","预测网格逐点采样","逐点计算远景网格，避免插值漏掉山脊与河岸。关闭后允许 VSS 自适应插值以加快生成。",0,1,true),
        new Option("predictionSurfaceFeatures","补全冰山与恶地地形","为 Java / RTF 预测补全原版地表扩展，保留冰山的海面下空隙；不生成真实区块。",0,1,true),
        new Option("sableCompatibility","航空学存储区保护","检测 Sable 船体存储区，避免把子空间方块记录为静态地形；不增加物理模拟距离。",0,1,true),
        new Option("adaptiveDistance","动态视距","依据实际帧耗时、堆内存和工作积压调节。目标帧率不是性能保证。",0,1,false),
        new Option("progressiveLoading","渐进加载","进入世界后由近到远缓慢扩展；可独立关闭。",0,1,false),
        new Option("frameBudget","加载工作预算","按经过的时间限制请求和根节点加载，避免高帧率导致更多后台工作。",0,1,false),
        new Option("nearFirstLoading","优先加载近处","按离相机的距离处理根节点。重新进世界生效。",0,1,true),
        new Option("limitCoarseMeshes","限制巨型粗模型","按模型投影面积限制粗模型；细模型未就绪时可能短暂留空。",0,1,false),
        new Option("cameraPlaneFix","相机近处细化","对跨越相机平面的粗模型继续细化，避免投影异常。",0,1,false),
        new Option("safeMeshGuards","网格有效性检查","阻止无效网格和子节点进入 GPU 队列。",0,1,false),
        new Option("strictRequestLimit","严格请求上限","多 GPU 线程竞争时仍遵守请求预算。",0,1,false),
        new Option("frustumCulling","视野外剔除","关闭后遍历阶段不剔除视野外节点；更耗性能。原版也有视锥剔除。",0,1,false),
        new Option("occlusionCulling","遮挡剔除","关闭后遍历阶段不剔除被遮住的节点。",0,1,false),
        new Option("skipStartupGc","跳过进世界强制 GC","跳过 NeoVoxy 渲染器创建时的显式完整 GC。",0,1,true),
        new Option("limitWorkers","限制后台线程","限制 Voxy 专用工作线程。此 NeoVoxy 基线没有启用 Sodium 线程共享。重新进世界生效。",0,1,true),
        new Option("limitMemory","限制缓存与显存预算","限制新建世界缓存和几何缓冲。重新进世界生效。",0,1,true),
        new Option("worldIsolation","多世界缓存隔离","优先使用后端身份隔离同种子世界；没有握手时按服务器地址及维度稳定保存。无身份的同地址同种子后端需填写手动世界标识。重新进服生效。",0,1,true),
        new Option("receiveServerLod","接收服务器 LOD","允许 VSS 握手和共享。重新进服生效。",0,1,true),
        new Option("existingTerrainFix","读取服务器已生成地形","关闭世界生成时仍可请求服务器已存盘区块。",0,1,false),
        new Option("minimumFps","目标最低帧率", "只调节本模组负载，无法保证整合包始终达到此帧率。",30,240,false),
        new Option("warmupSeconds","进入世界缓冲秒数","渐进加载在这段时间保持近处范围。",0,60,false),
        new Option("growthBlocksPerSecond","每秒扩展方块数","达到性能余量后扩展至 NeoVoxy 设定视距。",16,1024,false),
        new Option("backgroundDetail","视野外预加载","0=原版停止；1=少量粗模型；2=按距离细化。始终受独立请求预算约束。",0,2,false),
        new Option("backgroundRequestsPerSecond","视野外每秒请求","只在前台请求有余量时准备隐藏地形。",0,32,false),
        new Option("coarseScreenPercent","粗模型最大屏幕面积百分比","越小越能限制巨大粗模型，也可能延长空白等待。",1,25,false),
        new Option("backgroundThreads","后台线程上限","开启线程限制时使用；重新进世界生效。",1,8,true),
        new Option("sectionCacheMiB","区段缓存预算 MiB","仅限制空闲区段缓存，不是整个 Java 堆上限。重新进世界生效。",16,512,true),
        new Option("geometryMiB","几何缓冲预算 MiB","不是显卡总占用上限。重新进世界生效。",128,2048,true),
        new Option("meshRequestsPerSecond","每秒网格请求上限","性能调度开启时生效；启动或负载紧张时自动降低，越高加载越快也越占资源。",16,1024,false),
        new Option("rootUpdatesPerSecond","每秒远景列更新上限","限制根节点加载速度；低端建议 6，高端可提高至 32。",2,128,false)
    );
    public boolean originalRendering=true, adaptiveDistance=true, progressiveLoading=true, frameBudget=true,
        nearFirstLoading=true,limitCoarseMeshes=true,cameraPlaneFix=true,safeMeshGuards=true,
        strictRequestLimit=true,frustumCulling=true,occlusionCulling=true,skipStartupGc=true,
        limitWorkers=true,limitMemory=true,worldIsolation=true,receiveServerLod=true,existingTerrainFix=true;
    public boolean performanceScheduling=true,sharedPredictionBudget=true,sessionGuards=true,
        predictionCacheIsolation=true,sableCompatibility=true,xaeroMapIsolation=true;
    public boolean stablePrediction=true,predictionStrictSampling=true,predictionSurfaceFeatures=true;
    public int minimumFps=30,warmupSeconds=5,growthBlocksPerSecond=128,backgroundDetail=1,
        backgroundRequestsPerSecond=4,coarseScreenPercent=4,backgroundThreads=2,sectionCacheMiB=64,geometryMiB=512;
    public String backendOverride="";
    public int meshRequestsPerSecond=120,rootUpdatesPerSecond=12;
    public static final HxzSettings CONFIG=load();
    private static Path path(){return HxzPlatform.getInstance().getConfigDir().resolve("voxy-hxz-neovoxy.json");}
    private static HxzSettings load(){
        HxzSettings s=new HxzSettings();
        try {if(Files.exists(path())) {var r=new GsonBuilder().create().fromJson(Files.readString(path()),HxzSettings.class);if(r!=null)s=r;}}
        catch(Exception e){me.cortex.voxy.common.Logger.error("HXZ configuration load failed; defaults in use",e);}
        s.validate(); return s;
    }
    public boolean optimized(){return !originalRendering;}
    public boolean scheduling(){return performanceScheduling;}
    public void validate(){
        for(var o:OPTIONS)try{var f=getClass().getField(o.key());if(f.getType()==int.class)f.setInt(this,Math.clamp(f.getInt(this),o.min(),o.max()));}catch(ReflectiveOperationException e){throw new AssertionError(e);}
        if(backendOverride==null)backendOverride="";
    }
    public void save(){validate();try{me.cortex.voxy.hxz.config.ConfigIO.write(path(),new GsonBuilder().setPrettyPrinting().create().toJson(this));}
        catch(Exception e){throw new IllegalStateException("Cannot save HXZ settings",e);}}
    public Object get(String key){try{return getClass().getField(key).get(this);}catch(ReflectiveOperationException e){throw new IllegalArgumentException(key,e);}}
    public void set(String key,Object value){try{getClass().getField(key).set(this,value);validate();}catch(ReflectiveOperationException e){throw new IllegalArgumentException(key,e);}}
    public void profile(String name){
        if(name.equals("original")){originalRendering=true;performanceScheduling=false;return;}
        HxzSettings defaults=new HxzSettings();
        defaults.originalRendering=false;
        // Connection choices are not changed by a rendering preset.
        for(var o:OPTIONS)if(!Set.of("worldIsolation","receiveServerLod","existingTerrainFix","sessionGuards","predictionCacheIsolation","stablePrediction","predictionStrictSampling","predictionSurfaceFeatures","sableCompatibility","xaeroMapIsolation").contains(o.key()))set(o.key(),defaults.get(o.key()));
        if(name.equals("smooth")) {backgroundDetail=2;backgroundRequestsPerSecond=8;}
    }
    /** Performance presets preserve the chosen renderer and world/connection preferences. */
    public void performanceProfile(String name){
        performanceScheduling=true;adaptiveDistance=true;progressiveLoading=true;frameBudget=true;
        nearFirstLoading=true;skipStartupGc=true;limitWorkers=true;limitMemory=true;sharedPredictionBudget=true;
        warmupSeconds=5;minimumFps=30;
        boolean low=name.equals("low"),high=name.equals("quality");
        backgroundThreads=low?1:high?4:2;sectionCacheMiB=low?32:high?128:64;geometryMiB=low?256:high?1024:512;
        meshRequestsPerSecond=low?60:high?360:120;rootUpdatesPerSecond=low?6:high?32:12;
        growthBlocksPerSecond=low?64:high?256:128;validate();
    }
}
