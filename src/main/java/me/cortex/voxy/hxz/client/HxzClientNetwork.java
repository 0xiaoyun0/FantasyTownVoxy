package me.cortex.voxy.hxz.client;

import dev.xantha.vss.networking.payloads.HandshakeC2SPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class HxzClientNetwork {
    public static boolean isCurrent(net.neoforged.neoforge.network.handling.IPayloadContext context,boolean configuration){
        if(!me.cortex.voxy.hxz.HxzSettings.CONFIG.sessionGuards)return true;
        if(context.connection().getPacketListener()!=(Object)context.listener())return false;
        return configuration||Minecraft.getInstance().getConnection()==(Object)context.listener();
    }
    public static void beginConfiguration(){
        if(!me.cortex.voxy.hxz.HxzSettings.CONFIG.sessionGuards)return;
        if(Minecraft.getInstance().getConnection()!=null||Minecraft.getInstance().level!=null)
            dev.xantha.vss.networking.client.VSSClientNetworking.stopClientSessionForWorldShutdown();
        dev.xantha.vss.networking.client.ClientConnectionIdentity.endSession();
        AdminClient.clear();
    }
    public static boolean serverSupportsSharing() {
        var connection=Minecraft.getInstance().getConnection();
        return connection!=null && NetworkRegistry.hasChannel((ICommonPacketListener)(Object)connection,HandshakeC2SPayload.TYPE.id());
    }
    public static void sendIfSupported(CustomPacketPayload payload) {
        var connection=Minecraft.getInstance().getConnection();
        if(connection!=null && NetworkRegistry.hasChannel((ICommonPacketListener)(Object)connection,payload.type().id()))
            PacketDistributor.sendToServer(payload);
    }
}
