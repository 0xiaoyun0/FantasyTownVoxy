package me.cortex.voxy.hxz.client;

import java.util.*;
import com.google.gson.*;
import me.cortex.voxy.hxz.config.*;
import me.cortex.voxy.hxz.platform.AdminNetwork;
import net.minecraft.client.Minecraft;

public final class AdminClient {
    public static boolean allowed,updated,pending;
    private static long sentAt;
    private static long lastSent;
    private static AdminNetwork.Request queued;
    private static net.minecraft.client.multiplayer.ClientPacketListener queuedConnection;
    private static final Map<String,String> staged=new LinkedHashMap<>();
    public static String status="";
    public static String message="进入装有本版 Fantasy Town Voxy 的服务器后读取配置；需要 OP 2 级权限。";
    private static long revision;
    private static List<PanelOption> options=List.of();
    public static List<PanelOption> options(){return options;}
    public static boolean editable(String key){return allowed&&!pending&&options.stream().anyMatch(o->o.id().equals(key));}
    public static Object value(String key,Object fallback){return options.stream().filter(o->o.id().equals(key)).findFirst().map(o->o.read().get()).orElse(fallback);}
    public static void stage(String key,Object value){if(!SodiumPanel.refreshing&&editable(key))staged.put(key,String.valueOf(value));}
    public static void flush(){if(staged.isEmpty())return;var changes=Map.copyOf(staged);staged.clear();request("set",changes);}
    public static void tick(){
        if(queued!=null){
            if(Minecraft.getInstance().getConnection()!=queuedConnection){clear();return;}
            if(System.nanoTime()-lastSent>=300_000_000L)sendQueued();
        }
        if(pending&&System.nanoTime()-sentAt>10_000_000_000L){clear();message="服务器没有及时回复。请点击读取配置重试；不能把本地值视为服务器已应用。";}
    }
    private static void sendQueued(){
        HxzClientNetwork.sendIfSupported(queued);queued=null;queuedConnection=null;lastSent=System.nanoTime();
    }
    public static void receive(AdminNetwork.Response response){
        pending=false;
        try{
            var root=JsonParser.parseString(response.json()).getAsJsonObject();allowed=root.get("allowed").getAsBoolean();message=root.get("message").getAsString();updated=true;
            if(!allowed){options=List.of();status="";return;}revision=root.get("revision").getAsLong();status=root.get("status").getAsString();List<PanelOption> list=new ArrayList<>();
            for(var item:root.getAsJsonArray("options")){
                var o=item.getAsJsonObject();var primitive=o.getAsJsonPrimitive("value");Object value=primitive.isBoolean()?primitive.getAsBoolean():primitive.getAsInt();
                list.add(new PanelOption(o.get("id").getAsString(),o.has("page")?o.get("page").getAsString():"高级参数",o.get("label").getAsString(),o.get("help").getAsString(),o.get("min").getAsInt(),o.get("max").getAsInt(),List.of(),List.of(),value,()->value,v->{}));
            }
            options=List.copyOf(list);
        }catch(RuntimeException e){clear();message="服务器面板回复格式不兼容。";updated=true;}
    }
    public static void request(String action,Map<String,String> changes){
        if(pending){message="上一项服务器操作尚未完成，请稍候。";return;}
        var connection=Minecraft.getInstance().getConnection();
        if(connection==null||!connection.hasChannel(AdminNetwork.Request.TYPE)){clear();message="此服务器未提供 HXZ 管理面板。客户端设置仍可正常使用。";updated=true;return;}
        JsonObject object=new JsonObject();object.addProperty("action",action);object.addProperty("revision",revision);object.add("values",ConfigIO.GSON.toJsonTree(changes));
        // A fast read -> Apply still obeys the server's 250 ms admission limit.
        // Keep just this one request and bind it to the original play listener.
        queued=new AdminNetwork.Request(ConfigIO.GSON.toJson(object));queuedConnection=connection;
        pending=true;sentAt=System.nanoTime();message="等待服务器校验权限并返回结果…";
        if(sentAt-lastSent>=300_000_000L)sendQueued();
        if(net.caffeinemc.mods.sodium.client.config.ConfigManager.CONFIG!=null)net.caffeinemc.mods.sodium.client.config.ConfigManager.CONFIG.invalidateGlobalRebuildDependents();
    }
    public static void clear(){allowed=false;pending=false;queued=null;queuedConnection=null;options=List.of();revision=0;status="";staged.clear();updated=true;}
}
