package me.cortex.voxy.hxz.mixin;
import me.cortex.voxy.hxz.core.SessionNamespaces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="dev.xantha.vss.networking.client.ClientConnectionIdentity", remap=false)
public class VssIdentityMixin {
    @Shadow
    private static void acceptServerIdentity(String server, boolean shared, String node) {
        throw new AssertionError();
    }
    @Inject(method="acceptServerIdentity(Ljava/lang/String;ZLjava/lang/String;)V",at=@At("HEAD"))
    private static void hxz$capture(String server, boolean shared, String node, CallbackInfo ci) {
        SessionNamespaces.offer(server,shared,node);
    }
    @Inject(method="beginSession(Ljava/lang/String;Z)V",at=@At("RETURN"))
    private static void hxz$handoff(String address, boolean realm, CallbackInfo ci) {
        // VSS can attach CONFIGURATION identity to an old PLAY session. Restore
        // it after the new listener consumes VSS's own pending slot, before
        // prediction or Voxy resolves storage paths for this backend.
        var identity = SessionNamespaces.pendingIdentity();
        if (identity != null) acceptServerIdentity(identity.server(),identity.shared(),identity.node());
    }
}
