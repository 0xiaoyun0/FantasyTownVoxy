package me.cortex.voxy.hxz.mixin;
import me.cortex.voxy.client.VoxyClientInstance;
import me.cortex.voxy.hxz.HxzSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=VoxyClientInstance.class,remap=false)
public class HxzWorkersMixin {
    @ModifyArg(method="updateDedicatedThreads",at=@At(value="INVOKE",target="Lme/cortex/voxy/client/VoxyClientInstance;setNumThreads(I)V"),index=0)
    private int hxz$workers(int original){var s=HxzSettings.CONFIG;return s.scheduling()&&s.limitWorkers?Math.min(original,s.backgroundThreads):original;}
}
