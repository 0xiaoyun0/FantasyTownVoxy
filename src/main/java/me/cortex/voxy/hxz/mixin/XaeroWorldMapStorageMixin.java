package me.cortex.voxy.hxz.mixin;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="xaero.map.MapProcessor",remap=false)
public class XaeroWorldMapStorageMixin {
    @Inject(method="getMainId(ILnet/minecraft/client/multiplayer/ClientPacketListener;)Ljava/lang/String;",at=@At("RETURN"),cancellable=true,require=0)
    private void hxz$root(int version,ClientPacketListener listener,CallbackInfoReturnable<String> ci){
        ci.setReturnValue(me.cortex.voxy.hxz.client.XaeroMapScopes.root(ci.getReturnValue(),listener));
    }
}
