package me.cortex.voxy.hxz.mixin;

import dev.xantha.vss.networking.client.ClientLodBackpressure;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pause only new scans; identity, timeout handling and already received transfers keep running. */
@Mixin(targets="dev.xantha.vss.networking.client.LodRequestManager",remap=false)
public class ClientLodBackpressureMixin {
    @Inject(method="scanAndSendWithBudget",at=@At("HEAD"),cancellable=true)
    private void hxz$waitForIngest(ClientLevel level,LocalPlayer player,CallbackInfo ci) {
        if(ClientLodBackpressure.pauseRequests())ci.cancel();
    }

    @Redirect(method="timeoutSweep",at=@At(value="INVOKE",
            target="Ldev/xantha/vss/networking/client/ClientRequestTracker;drainTimedOut(J)Ljava/util/List;"))
    private java.util.List<?> hxz$keepReceivedColumnsCurrent(@Coerce Object tracker,long nowNanos) {
        return ClientLodBackpressure.drainTimedOut(tracker,nowNanos);
    }
}
