package me.cortex.voxy.hxz.platform;

import java.util.*;
import java.util.function.Consumer;
import com.google.gson.*;
import me.cortex.voxy.hxz.config.ConfigIO;
import me.cortex.voxy.hxz.server.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.HandlerThread;

public final class AdminNetwork {
    public record Request(String json)implements CustomPacketPayload{
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("voxy","hxz_admin_request_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,8192),b->new Request(b.readUtf(8192)));
        @Override public Type<Request> type(){return TYPE;}
    }
    public record Response(String json)implements CustomPacketPayload{
        public static final Type<Response> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("voxy","hxz_admin_response_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Response> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,65536),b->new Response(b.readUtf(65536)));
        @Override public Type<Response> type(){return TYPE;}
    }
    public static Consumer<Response> client=p->{};
    private record Pending(ServerPlayer player,Request request){}
    private static final LinkedHashMap<UUID,Pending> pending=new LinkedHashMap<>();
    private static final Map<UUID,Long> last=new HashMap<>();
    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("hxz-panel-1").optional();
        registrar.executesOn(HandlerThread.NETWORK).playToServer(Request.TYPE,Request.CODEC,AdminNetwork::receive);
        registrar.executesOn(HandlerThread.MAIN).playToClient(Response.TYPE,Response.CODEC,(p,c)->client.accept(p));
    }
    private static synchronized void receive(Request request,IPayloadContext context){
        if(!(context.player() instanceof ServerPlayer player))return;
        long now=System.nanoTime();UUID id=player.getUUID();
        if(pending.size()>=32||now-last.getOrDefault(id,0L)<250_000_000L)return;
        if(last.size()>512)last.entrySet().removeIf(e->now-e.getValue()>60_000_000_000L);
        if(last.size()>=512&&!last.containsKey(id))return;
        last.put(id,now);pending.put(id,new Pending(player,request));
    }
    public static void tick(ServerTickEvent.Post event){
        for(int i=0;i<2;i++){
            Pending work;
            synchronized(AdminNetwork.class){var iterator=pending.values().iterator();if(!iterator.hasNext())return;work=iterator.next();iterator.remove();}
            var player=work.player();if(player.server!=event.getServer()||player.hasDisconnected())continue;
            JsonObject response;
            try{
                if(!player.createCommandSourceStack().hasPermission(2))throw new IllegalArgumentException("需要服务器 OP 2 级权限；客户端无法自行授予权限");
                var body=JsonParser.parseString(work.request().json()).getAsJsonObject();String action=body.get("action").getAsString();
                switch(action){
                    case "get" -> {}
                    case "set" -> ServerPanel.apply(event.getServer(),body.getAsJsonObject("values"),body.get("revision").getAsLong());
                    case "reload" -> ServerPanel.reload(event.getServer());
                    case "reset" -> ServerTickGuard.reset();
                    default -> throw new IllegalArgumentException("未知面板操作");
                }
                response=ServerPanel.snapshot(action.equals("get")?"已读取服务器实际配置。":"服务器配置已热更新。");
            }catch(Exception error){response=new JsonObject();response.addProperty("allowed",false);response.addProperty("message",error.getMessage()==null?"操作失败，未应用":error.getMessage());}
            dev.xantha.vss.networking.VSSNetworking.sendToPlayer(player,new Response(ConfigIO.GSON.toJson(response)));
        }
    }
    public static synchronized void clear(){pending.clear();last.clear();}
}
