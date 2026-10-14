package me.cortex.voxy.hxz.mixin;

import dev.xantha.vss.networking.server.sending.QueuedColumnSender;
import dev.xantha.vss.networking.server.state.PlayerRequestState;
import me.cortex.voxy.hxz.server.ServerDownloads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=QueuedColumnSender.class,remap=false)
public class DownloadSenderMixin {
    @Inject(method="flush",at=@At("HEAD"),cancellable=true)
    private void hxz$window(MinecraftServer server,CallbackInfo ci){if(!ServerDownloads.allowed())ci.cancel();}
    @Inject(method="sendQueuedPayloadBatch",at=@At(value="INVOKE",target="Ldev/xantha/vss/networking/server/sending/PlayerSendWindow;acquire(I)Ljava/lang/Runnable;"),cancellable=true)
    private void hxz$bytes(ServerPlayer player,PlayerRequestState state,PlayerRequestState.QueuedPayloadBatch batch,long limit,CallbackInfoReturnable<Integer> ci){
        int bytes=batch.nextPayload().wireBytes();
        if(!state.sendWindow().hasRoom(bytes)||!ServerDownloads.take(player.getUUID(),bytes))ci.setReturnValue(0);
    }
    @Inject(method="effectiveExpiryBandwidth",at=@At("RETURN"),cancellable=true)
    private void hxz$expiry(long total,PlayerRequestState state,int players,CallbackInfoReturnable<Long> ci){ci.setReturnValue(ServerDownloads.personalRate(ci.getReturnValue()));}
    @Redirect(method="acknowledgeQueuedRequests",at=@At(value="INVOKE",target="Ldev/xantha/vss/networking/server/sending/PlayerSendWindow;acquire(I)Ljava/lang/Runnable;"))
    private Runnable hxz$ack(dev.xantha.vss.networking.server.sending.PlayerSendWindow window,int bytes,ServerPlayer player,PlayerRequestState state){
        return window.hasRoom(bytes)&&ServerDownloads.take(player.getUUID(),bytes)?window.acquire(bytes):null;
    }
}
