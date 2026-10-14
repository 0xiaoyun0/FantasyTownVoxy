package me.cortex.voxy.hxz.mixin;

import me.cortex.voxy.hxz.core.SessionNamespaces;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConfigurationPacketListenerImpl.class)
public class ConfigurationSessionMixin {
    @Inject(method="<init>",at=@At("RETURN"))
    private void hxz$beginConfiguration(CallbackInfo ci) {
        SessionNamespaces.beginConfiguration();
        me.cortex.voxy.hxz.client.HxzClientNetwork.beginConfiguration();
    }
}
