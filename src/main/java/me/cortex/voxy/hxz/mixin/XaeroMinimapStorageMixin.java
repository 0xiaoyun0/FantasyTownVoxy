package me.cortex.voxy.hxz.mixin;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets="xaero.hud.minimap.world.state.MinimapWorldStateUpdater",remap=false)
public class XaeroMinimapStorageMixin {
    @Shadow @Final private ClientPacketListener connection;
    @ModifyArg(method="getAutoRootContainerPath(I)Lxaero/hud/path/XaeroPath;",at=@At(value="INVOKE",target="Lxaero/hud/path/XaeroPath;root(Ljava/lang/String;)Lxaero/hud/path/XaeroPath;"),index=0,require=0)
    private String hxz$root(String original){return me.cortex.voxy.hxz.client.XaeroMapScopes.root(original,connection);}
}
