package me.cortex.voxy.hxz.server;

import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import me.cortex.voxy.hxz.config.ConfigIO;
import me.cortex.voxy.hxz.platform.HxzPlatform;
import dev.xantha.vss.config.VSSServerConfig;
import dev.xantha.vss.networking.server.VSSServerNetworking;
import net.minecraft.server.MinecraftServer;

/** Only this whitelist can be changed remotely; no file paths, command strings or arbitrary reflection. */
public final class ServerPanel {
    private static long revision(){
        var crc=new java.util.zip.CRC32C();
        byte[] bytes=(ConfigIO.GSON.toJson(ServerSafetyConfig.CONFIG)+ConfigIO.GSON.toJson(VSSServerConfig.CONFIG)).getBytes(java.nio.charset.StandardCharsets.UTF_8);crc.update(bytes,0,bytes.length);return crc.getValue();
    }
    private static final Map<String,String> SAFETY_NAMES=Map.ofEntries(
        Map.entry("enabled","服务器负载保护总开关"),Map.entry("limitRequests","限制玩家 LOD 请求"),Map.entry("tickBudget","限制每 tick 远景耗时"),
        Map.entry("pauseOnOverload","服务器繁忙时暂停共享"),Map.entry("limitResources","限制后台线程与缓存"),Map.entry("isolateErrors","共享出错时暂停共享并保留服务器"),
        Map.entry("allowLodGeneration","允许 LOD 请求生成新地形"),Map.entry("backgroundPreload","允许后台预读地形"),Map.entry("filterSablePlots","过滤航空学船体存储区"),
        Map.entry("cacheProfileTransfers","复用地形参数传输编码"),Map.entry("profileCacheMiB","地形参数编码缓存上限（MiB）"),
        Map.entry("downloadScheduleEnabled","按每日时段禁止 LOD 下载"),Map.entry("downloadBlockStartHour","禁止下载：开始小时（0—23）"),Map.entry("downloadBlockStartMinute","禁止下载：开始分钟（0—59）"),
        Map.entry("downloadBlockEndHour","禁止下载：结束小时（0—23）"),Map.entry("downloadBlockEndMinute","禁止下载：结束分钟（0—59）"),
        Map.entry("downloadUtcOffsetMinutes","时区偏移（UTC 分钟）"),
        Map.entry("downloadLimitPerPlayer","启用每玩家下载限速"),Map.entry("downloadPlayerBytesPerSecond","每玩家下载上限（B/s）"),
        Map.entry("columnsPerTick","全服每 tick 请求上限（列）"),Map.entry("columnsPerPlayerTick","每玩家每 tick 请求上限（列）"),
        Map.entry("queuedColumns","全服排队上限（列）"),Map.entry("queuedColumnsPerPlayer","每玩家排队上限（列）"),
        Map.entry("tickBudgetMillis","每 tick 远景时间预算（毫秒）"),Map.entry("pauseAboveMillis","暂停阈值：服务器 tick 耗时（毫秒）"),
        Map.entry("heapPercent","暂停阈值：堆内存占用（%）"),Map.entry("cooldownSeconds","暂停后冷却时间（秒）"),
        Map.entry("diskTasks","磁盘任务排队上限"),Map.entry("workerThreads","磁盘工作线程上限"),Map.entry("completionTasks","完成回调排队上限"),
        Map.entry("sendPackets","每 tick 发送包数上限"),Map.entry("sendQueueMiB","每玩家发送队列上限（MiB）"),
        Map.entry("cacheMiB","内存 LOD 缓存上限（MiB）"),Map.entry("packingMiB","编码队列上限（MiB）"));
    public static String group(String key){
        if(key.startsWith("safety.download")||key.equals("vss.totalBandwidthBytesPerSecond"))return "下载限制";
        if(key.equals("safety.cacheProfileTransfers")||key.equals("safety.profileCacheMiB"))return "传输与缓存";
        if(key.startsWith("safety."))return "负载保护";
        if(Set.of("enabled","enableChunkGeneration","enablePredictionSync","sendDistance","maxGenerationDistance","generationDistanceChunks").contains(key.substring(4)))return "共享与地形";
        if(key.toLowerCase(Locale.ROOT).matches(".*(cache|disk|bandwidth|send|packet|packing|storage).*"))return "传输与缓存";
        return "高级参数";
    }
    private static String safetyHelp(String key,boolean bool,int min,int max){
        String detail=switch(key){
            case "enabled"->"限制请求、线程、缓存和共享耗时。默认开启；航空学存储区过滤独立控制。";
            case "filterSablePlots"->"排除 Sable 专用船体坐标区，避免将其作为静态地形共享；不会加载新区块或增加物理模拟距离。";
            case "cacheProfileTransfers"->"多名玩家进入同一世界时复用地形参数编码，减少重复分配；参数版本变化后重新编码。独立于负载保护总开关。";
            case "profileCacheMiB"->"最多保留 4 份编码，合计不超过此值；单份超限则发送后释放。关闭复用会清空缓存。首次采集地形参数仍可能较慢。";
            case "downloadScheduleEnabled"->"默认关闭。按下方服务器时区每天暂停下载；17:00—23:00 表示含 17:00、不含 23:00。跨午夜也支持；开始和结束相同表示全天禁止。已有本地远景继续使用，到点自动恢复。独立于负载保护总开关。";
            case "downloadBlockStartHour","downloadBlockStartMinute","downloadBlockEndHour","downloadBlockEndMinute"->"每日禁止下载区间的起止时刻。小时和分钟分别填写；例如开始 17 / 0，结束 23 / 0。时段开关关闭时不限制下载时间。";
            case "downloadUtcOffsetMinutes"->"服务器统一按此时区执行，与玩家电脑时间无关。北京时间填 480，UTC 填 0。固定 UTC 偏移，不自动跟随夏令时。";
            case "downloadLimitPerPlayer"->"服务端强制约束每个玩家，客户端提高下载设置无法绕过；全服总限速仍同时生效。关闭后只受全服和客户端限速约束。";
            case "downloadPlayerBytesPerSecond"->"每个玩家每秒可发送的 LOD 字节预算，按 UUID 计费，重连不重置当前预算。包含 LOD 包和排队确认；不限制原版游戏流量。单个数据包会产生短时波动，空闲不会积攒大量突发额度。";
            case "limitRequests"->"限制每个玩家及全服的请求速率和排队数量，避免大量请求挤占服务器 tick。";
            case "tickBudget"->"预算耗尽后把剩余工作留到后续 tick；不是对单次操作的强制超时。";
            case "pauseOnOverload"->"连续慢 tick 或内存紧张时暂缓新的 LOD 请求，冷却后自动恢复。";
            case "limitResources"->"为 VSS 后台线程、发送队列和缓存施加下方上限。";
            case "isolateErrors"->"普通共享异常只暂停 LOD 服务；修复原因后可用恢复按钮重试。";
            case "allowLodGeneration"->"默认关闭。还需开启 VSS 生成开关；共享已有区块无需生成新地形。";
            case "backgroundPreload"->"默认关闭。允许 VSS 提前读取服务器地形，会增加磁盘和 CPU 开销。";
            case "tickBudgetMillis"->"在工作单元之间检查预算，无法中断正在执行的模组调用。";
            default->"此限额影响共享加载速度和服务器占用；建议先使用默认值。";
        };
        return detail+(bool?"":" 允许范围 "+min+"—"+max+"。")+" 应用后热更新，与客户端渲染方式无关。";
    }
    public record Entry(String key,String label,String help,int min,int max,boolean bool){}
    public static List<Entry> entries(){
        List<Entry> list=new ArrayList<>();
        for(var o:ServerSafetyConfig.OPTIONS)list.add(new Entry("safety."+o.key(),SAFETY_NAMES.getOrDefault(o.key(),o.key()),
            safetyHelp(o.key(),o.bool(),o.min(),o.max()),o.min(),o.max(),o.bool()));
        Map<String,String> help;
        try{var field=VSSServerConfig.class.getDeclaredField("CONFIG_HELP");field.setAccessible(true);help=(Map<String,String>)field.get(null);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        for(var f:VSSServerConfig.class.getFields()){
            if(Modifier.isStatic(f.getModifiers())||Modifier.isTransient(f.getModifiers())||f.isAnnotationPresent(Deprecated.class)||f.getName().equals("sharedWorld"))continue;
            if(f.getType()!=int.class&&f.getType()!=boolean.class)continue;
            String description=help.getOrDefault(f.getName(),"VSS 参数 "+f.getName());
            String label=description.split("[；;，。]",2)[0].replaceFirst("^是否"," ").trim();
            if(f.getName().equals("enabled"))label="向玩家共享已有 LOD（总开关）";
            if(f.getName().equals("enableChunkGeneration"))label="VSS 请求生成新地形";
            if(f.getName().equals("enablePredictionSync"))label="向客户端提供地形预测资料（含种子）";
            if(f.getName().equals("totalBandwidthBytesPerSecond")){label="全服 LOD 下载总上限（B/s）";description="所有玩家合计的 LOD 发送预算，默认 500000 B/s。受网络包大小影响存在短时波动；不包含原版区块、聊天及初次地形预测参数同步。可填 62500—12500000，需先读取服务器配置。";}
            boolean bandwidth=f.getName().equals("totalBandwidthBytesPerSecond");
            list.add(new Entry("vss."+f.getName(),label,description+" HXZ 负载保护开启时，实际并发和资源用量还受保护上限约束。",bandwidth?62500:0,bandwidth?12500000:f.getType()==boolean.class?1:Integer.MAX_VALUE,f.getType()==boolean.class));
        }
        return list;
    }
    public static JsonObject snapshot(String message){
        JsonObject root=new JsonObject();root.addProperty("allowed",true);root.addProperty("revision",revision());root.addProperty("message",message);root.addProperty("status",(VSSServerConfig.CONFIG.enabled?ServerTickGuard.panelStatus():"管理员已关闭服务器共享")+"；"+ServerDownloads.status());
        JsonArray list=new JsonArray();
        for(var entry:entries()){
            JsonObject o=new JsonObject();o.addProperty("id",entry.key());o.addProperty("page",group(entry.key()));o.addProperty("label",entry.label());o.addProperty("help",entry.help());o.addProperty("min",entry.min());o.addProperty("max",entry.max());
            Object target=entry.key().startsWith("safety.")?ServerSafetyConfig.CONFIG:VSSServerConfig.CONFIG;
            try{o.add("value",ConfigIO.GSON.toJsonTree(target.getClass().getField(entry.key().substring(entry.key().indexOf('.')+1)).get(target)));}
            catch(ReflectiveOperationException e){throw new IllegalStateException(e);}list.add(o);
        }
        root.add("options",list);return root;
    }
    public static void apply(MinecraftServer server,JsonObject changes,long expectedRevision)throws Exception {
        if(expectedRevision!=revision())throw new IllegalArgumentException("配置已被其他管理员更新，请刷新后重试");
        if(changes.size()>96)throw new IllegalArgumentException("一次修改过多");
        var safety=ConfigIO.GSON.fromJson(ConfigIO.GSON.toJson(ServerSafetyConfig.CONFIG),ServerSafetyConfig.class);
        var vss=ConfigIO.GSON.fromJson(ConfigIO.GSON.toJson(VSSServerConfig.CONFIG),VSSServerConfig.class);
        Map<String,Entry> allowed=new HashMap<>();for(var e:entries())allowed.put(e.key(),e);
        for(var change:changes.entrySet()){
            Entry spec=allowed.get(change.getKey());if(spec==null)throw new IllegalArgumentException("不能通过面板修改此参数");
            Object value;
            if(spec.bool()){
                String raw=change.getValue().getAsString();if(!raw.equals("true")&&!raw.equals("false"))throw new IllegalArgumentException("无效开关值");value=Boolean.parseBoolean(raw);
            }else{int n=Integer.parseInt(change.getValue().getAsString());if(n<spec.min()||n>spec.max())throw new IllegalArgumentException("数值超出允许范围");value=n;}
            Object target=change.getKey().startsWith("safety.")?safety:vss;
            target.getClass().getField(change.getKey().substring(change.getKey().indexOf('.')+1)).set(target,value);
        }
        validate(vss);
        Path dir=HxzPlatform.getInstance().getConfigDir();
        ConfigIO.write(dir.resolve("voxy-hxz-server.json"),ConfigIO.GSON.toJson(safety));
        ConfigIO.write(dir.resolve("vss-server-config.json"),ConfigIO.GSON.toJson(vss));
        ConfigIO.copy(safety,ServerSafetyConfig.CONFIG);ConfigIO.copy(vss,VSSServerConfig.CONFIG);refresh(server);
    }
    private static void validate(VSSServerConfig value)throws Exception {var method=VSSServerConfig.class.getDeclaredMethod("validate");method.setAccessible(true);method.invoke(value);}
    public static void reload(MinecraftServer server)throws Exception {
        Path dir=HxzPlatform.getInstance().getConfigDir();
        var safety=ConfigIO.read(dir.resolve("voxy-hxz-server.json"),ServerSafetyConfig.class,ConfigIO.GSON);
        var vss=ConfigIO.read(dir.resolve("vss-server-config.json"),VSSServerConfig.class,ConfigIO.GSON);validate(vss);
        if(!Objects.equals(vss.serverIdentity,VSSServerConfig.CONFIG.serverIdentity)||!Objects.equals(vss.nodeIdentity,VSSServerConfig.CONFIG.nodeIdentity)||vss.sharedWorld!=VSSServerConfig.CONFIG.sharedWorld)
            throw new IllegalArgumentException("世界身份变更需重启服务器，已保留当前配置，防止在线玩家缓存混用");
        ConfigIO.copy(safety,ServerSafetyConfig.CONFIG);ConfigIO.copy(vss,VSSServerConfig.CONFIG);refresh(server);
    }
    private static void refresh(MinecraftServer server){ServerSafetyConfig.CONFIG.publish();VSSServerNetworking.applyRuntimeConfig();VSSServerNetworking.bumpAndRefreshSessionConfigs(server);}
}
