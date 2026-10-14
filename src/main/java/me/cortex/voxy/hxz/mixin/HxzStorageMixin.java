package me.cortex.voxy.hxz.mixin;
import java.nio.file.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets="me.cortex.voxy.client.VoxyClientInstance",remap=false,priority=900)
public class HxzStorageMixin {
    @Shadow private static Path getBasePath(){throw new AssertionError();}
    @Redirect(method="<init>",at=@At(value="INVOKE",target="Lme/cortex/voxy/client/VoxyClientInstance;getBasePath()Ljava/nio/file/Path;"))
    private Path hxz$separateFormat(){return getBasePath().resolve("hxz-neovoxy-0.6");}
}
