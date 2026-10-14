package me.cortex.voxy.hxz.mixin;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Xaero initializes AFTER this call, before ClientLevel exists. Capture BEFORE
// it, and only on the render thread (PacketUtils reschedules off-thread calls).
@Mixin(value=ClientPacketListener.class,priority=1500)
public class XaeroLoginScopeMixin {
    @Inject(method="handleLogin",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V"))
    private void hxz$mapScope(ClientboundLoginPacket packet,CallbackInfo ci){
        if(net.minecraft.client.Minecraft.getInstance().isSameThread())me.cortex.voxy.hxz.client.XaeroMapScopes.capture((ClientPacketListener)(Object)this,packet.commonPlayerSpawnInfo().seed());
    }
}
