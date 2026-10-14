package me.cortex.voxy.hxz.client;

import java.util.*;
import java.util.function.Supplier;
import me.cortex.voxy.hxz.HxzSettings;
import me.cortex.voxy.hxz.config.PanelOption;
import me.cortex.voxy.client.config.VoxyConfig;
import dev.xantha.vss.config.VSSClientConfig;

public final class ClientOptions {
    public static List<PanelOption> all(){
        List<PanelOption> out=new ArrayList<>();
        var defaults=new HxzSettings();
        for(var o:HxzSettings.OPTIONS){
            String page=Set.of("worldIsolation","receiveServerLod","existingTerrainFix").contains(o.key())?"服务器共享":
                Set.of("sessionGuards","sableCompatibility","xaeroMapIsolation").contains(o.key())?"数据与兼容":Set.of("predictionCacheIsolation","stablePrediction","predictionStrictSampling","predictionSurfaceFeatures").contains(o.key())?"地形预测":
                o.key().equals("originalRendering")?"基本显示":Set.of("frustumCulling","occlusionCulling","backgroundDetail","backgroundRequestsPerSecond","coarseScreenPercent","limitCoarseMeshes","cameraPlaneFix","safeMeshGuards").contains(o.key())?"HXZ 画面优化":"性能与加载";
            String help=o.help().replace("重新进世界完全生效。","应用时自动重建远景。")
                .replace("重新进世界生效。","应用后自动更新远景。")
                .replace("重新进服生效。","应用后更新共享会话。")
                .replace("重新进世界/服务器完全生效。","应用后更新。");
            if(page.startsWith("HXZ"))help="仅在 HXZ 渲染模式下生效。"+help;
            if(o.key().equals("xaeroMapIsolation"))help=o.help();
            String label=o.key().equals("originalRendering")?"渲染方式":o.label();
            List<String> values=o.key().equals("originalRendering")?List.of("neovoxy","hxz"):o.key().equals("backgroundDetail")?List.of("0","1","2"):List.of();
            List<String> labels=o.key().equals("originalRendering")?List.of("NeoVoxy 原版（默认）","HXZ 可调优化"):o.key().equals("backgroundDetail")?List.of("停止预加载","少量粗模型","按距离准备细节"):List.of();
            Object defaultValue=o.key().equals("originalRendering")?"neovoxy":o.key().equals("backgroundDetail")?"1":defaults.get(o.key());
            out.add(new PanelOption("hxz_"+o.key().toLowerCase(Locale.ROOT),page,label,help,o.min(),o.max(),values,labels,defaultValue,
                ()->o.key().equals("originalRendering")?(HxzSettings.CONFIG.originalRendering?"neovoxy":"hxz"):o.key().equals("backgroundDetail")?Integer.toString(HxzSettings.CONFIG.backgroundDetail):HxzSettings.CONFIG.get(o.key()),v->{
                    Object value=o.key().equals("originalRendering")?v.equals("neovoxy"):o.key().equals("backgroundDetail")?Integer.valueOf(v.toString()):v;
                    HxzSettings.CONFIG.set(o.key(),value);
                    if(o.key().equals("receiveServerLod"))VSSClientConfig.CONFIG.receiveServerLods=(Boolean)value;
                    LiveClientConfig.changed(o.restart());
                }));
        }
        add(out,"voxy","基本显示","enabled","远景功能总开关","关闭后停止远景渲染和记录；原版区块照常显示。",0,1,true,true);
        add(out,"voxy","基本显示","enableRendering","显示远景","只控制显示；关闭后仍可记录经过的区块。",0,1,true,true);
        add(out,"voxy","基本显示","sectionRenderDistance","远景最大距离（512 方块/级）","例如 16 = 8192 方块。HXZ 动态视距以此为上限。",2,64,16,false);
        add(out,"voxy","基本显示","subDivisionSize","远景模型粗细（像素）","数值越小，模型越精细，显存和绘制负担越高。默认 64。",28,256,64,false);
        add(out,"voxy","基本显示","renderVanillaFog","原版雾效","让远景服从原版雾；光影包也可能控制雾。",0,1,false,false);
        add(out,"voxy","数据与兼容","ingestEnabled","记录玩家经过的区块","把实际接收到的区块存为本地 LOD，供以后显示。",0,1,true,false);
        add(out,"voxy","数据与兼容","serviceThreads","NeoVoxy 工作线程数","线程越多加载越快，也会与整合包争抢 CPU。HXZ 线程限制开启时还受其上限约束。",1,Math.max(1,Runtime.getRuntime().availableProcessors()),new VoxyConfig().serviceThreads,false);
        add(out,"voxy","数据与兼容","dontUseSodiumBuilderThreads","使用独立工作线程","不借用钠的区块构建线程。适合排查线程竞争。",0,1,false,false);
        add(out,"voxy","数据与兼容","renderStatistics","显示远景调试统计","排障使用；普通游玩可关闭。",0,1,false,true);
        add(out,"vss","服务器共享","lodDistanceChunks","请求服务器地形半径（区块）","0 = 跟随显示距离；1 区块 = 16 方块。受服务器管理员设置限制。",0,8192,0,false);
        add(out,"vss","服务器共享","desiredBandwidthKbps","接收带宽偏好（kbps）","0 = 自动；8000 kbps 约等于 1 MB/s。服务器可能进一步限速。",0,100000,2000,false);
        add(out,"vss","服务器共享","offThreadSectionProcessing","后台处理接收数据","在工作线程解码服务器 LOD，减少主线程卡顿。",0,1,true,false);
        add(out,"vss","数据与兼容","enableXaeroMapBridge","同步到 Xaero 地图","让已接收的真实区块用于 Xaero 地图。未安装 Xaero 时不会启用桥接。",0,1,false,false);
        add(out,"vss","地形预测","enablePrediction","预测未探索的远景","需要服务器提供种子和生成器配置。支持原 VSS 的 RTF / NeoTerraForged 兼容；预测不能代替真实区块。",0,1,true,false);
        choice(out,"performanceTier","预测计算强度",List.of("low","medium","high"),List.of("低：优先帧率","中：均衡","高：优先生成速度"),"medium","仅影响计算预算，不改变显示距离或画质。");
        choice(out,"predictionDetail","预测模型细节",List.of("low","normal","high","extreme"),List.of("低","标准","高","极高"),"normal","越高越精细，显存和 CPU 占用也越高。");
        choice(out,"predictionVegetationDensity","预测植被数量",List.of("low","medium","high"),List.of("稀疏 25%","适中 50%","完整 100%"),"medium","仅控制远景植被显示，不改变世界生成。");
        add(out,"vss","地形预测","predictionDistanceBlocks","预测最远距离（方块）","独立于服务器真实 LOD 请求半径；开启性能调度后可动态限制它。",1024,65536,4096,false);
        add(out,"vss","地形预测","predictionFineDistanceBlocks","近处精细预测距离（方块）","越大越精细，也更占 CPU。",256,4096,512,false);
        add(out,"vss","地形预测","predictionSurfaceDistanceBlocks","真实地形外精修范围（方块）","在已知地形边缘外补充地表细节。",128,2048,768,false);
        add(out,"vss","地形预测","predictionStructures","预测建筑","预测生成器支持的地表结构，不包括玩家建筑。",0,1,true,false);
        add(out,"vss","地形预测","predictionSpyglassLoading","望远镜目标精修","放大观察时额外准备目标附近的地形细节。",0,1,true,false);
        add(out,"vss","预测性能与画面","predictionBackgroundWorkers","视野外预测任务上限","这不是总线程数；仍受预测计算强度限制。",1,4,1,false);
        add(out,"vss","预测性能与画面","predictionRefinementWorkers","精修任务上限","0 = 跟随计算强度；与其他预测任务共享 CPU 预算。",0,32,2,false);
        add(out,"vss","预测性能与画面","rememberTerrain","保存预测缓存","保存预测结果，加快重访。与真实 LOD 数据分开保存。",0,1,true,false);
        add(out,"vss","预测性能与画面","predictionCompressMeshes","压缩预测模型内存","降低 CPU 内存占用；新加载的模型采用此设置。",0,1,true,false);
        add(out,"vss","预测性能与画面","predictionSupersample","近处预测超采样","改善近处细节，增加生成开销。",0,1,false,false);
        add(out,"vss","预测性能与画面","predictionAntialiasing","远景抗锯齿","改善无光影时的远景锯齿；开启光影时由光影接管。",0,1,true,false);
        add(out,"vss","预测性能与画面","predictionFog","预测远景雾","柔化预测远景边缘。",0,1,true,false);
        add(out,"vss","预测性能与画面","predictionPausedFps","单人暂停时帧率上限","0 = 不限制；多人游戏菜单不会触发此限制。",0,260,30,false);
        add(out,"vss","数据与兼容","debugLogging","详细诊断日志","仅排障时开启，长期开启会增加日志量。",0,1,false,false);
        out.add(new PanelOption("backend_override","数据与兼容","手动世界标识","通常留空，由服务器提供身份。无身份的同地址同种子后端须使用不同值；重新进服后切换 LOD 和 Xaero 地图目录，不删除旧数据。",0,128,List.of(),List.of(),"",()->HxzSettings.CONFIG.backendOverride,v->{HxzSettings.CONFIG.backendOverride=v.toString();LiveClientConfig.changed(false);}));
        return List.copyOf(out);
    }
    private static Object config(String scope){return scope.equals("voxy")?VoxyConfig.CONFIG:VSSClientConfig.CONFIG;}
    private static void add(List<PanelOption> out,String scope,String page,String field,String label,String help,int min,int max,Object def,boolean reload){
        out.add(new PanelOption(scope+"_"+field.toLowerCase(Locale.ROOT),page,label,help,min,max,List.of(),List.of(),def,()->get(config(scope),field),v->{set(config(scope),field,v);LiveClientConfig.changed(reload);}));
    }
    private static void choice(List<PanelOption> out,String field,String label,List<String> values,List<String> labels,String def,String help){
        out.add(new PanelOption("vss_"+field.toLowerCase(Locale.ROOT),"地形预测",label,help,0,values.size()-1,values,labels,def,()->get(VSSClientConfig.CONFIG,field),v->{set(VSSClientConfig.CONFIG,field,v);LiveClientConfig.changed(false);}));
    }
    public static Object get(Object target,String name){try{Object v=target.getClass().getField(name).get(target);return v instanceof Float f?Math.round(f):v;}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static void set(Object target,String name,Object value){try{var f=target.getClass().getField(name);if(f.getType()==float.class)f.setFloat(target,((Number)value).floatValue());else f.set(target,value);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
}
