package me.cortex.voxy.hxz.mixin;
import me.cortex.voxy.hxz.HxzSettings;
import me.cortex.voxy.hxz.client.HxzClient;
import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.rendering.*;
import me.cortex.voxy.client.core.rendering.building.RenderGenerationService;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=VoxyRenderSystem.class,remap=false)
public class HxzRenderMixin {
    @Shadow @Final private RenderGenerationService renderGen;
    @Shadow @Final private RenderDistanceTracker renderDistanceTracker;
    @Inject(method="<init>",at=@At("HEAD"))
    private static void hxz$reset(CallbackInfo ci){HxzClient.reset();}
    @Redirect(method="<init>",at=@At(value="INVOKE",target="Ljava/lang/System;gc()V"))
    private void hxz$gc(){var s=HxzSettings.CONFIG;if(!s.scheduling()||!s.skipStartupGc)System.gc();}
    @ModifyArg(method="<init>",at=@At(value="INVOKE",target="Lme/cortex/voxy/client/core/rendering/RenderDistanceTracker;setRenderDistance(I)V"),index=0)
    private int hxz$initial(int original){return HxzClient.initialDistance(original);}
    @Inject(method="renderOpaque",at=@At("HEAD"))
    private void hxz$frame(Viewport<?> viewport,CallbackInfo ci){if(viewport==null)return;HxzClient.frame(renderGen.getTaskCount());renderDistanceTracker.setRenderDistance((int)Math.ceil(HxzClient.effectiveDistance()));}
    @Inject(method="getGeometryBufferSize",at=@At("RETURN"),cancellable=true)
    private static void hxz$geometry(CallbackInfoReturnable<Long> cir){var s=HxzSettings.CONFIG;if(s.scheduling()&&s.limitMemory)cir.setReturnValue(Math.min(cir.getReturnValue(),s.geometryMiB*1048576L));}
}
