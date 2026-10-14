package me.cortex.voxy.hxz.mixin;

import me.cortex.voxy.hxz.client.HxzClientNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets="dev.xantha.vss.networking.client.VSSClientNetworking",remap=false)
public class VssOptionalServerMixin {
    @Inject(method="tryPendingHandshake",at=@At("HEAD"),cancellable=true)
    private static void hxz$optionalServer(CallbackInfo ci) {
        if(!me.cortex.voxy.hxz.HxzSettings.CONFIG.receiveServerLod || !HxzClientNetwork.serverSupportsSharing()) ci.cancel();
    }
}
