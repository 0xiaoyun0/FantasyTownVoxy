package me.cortex.voxy.hxz.mixin;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.client.core.rendering.hierachical.HierarchicalOcclusionTraverser;
import me.cortex.voxy.hxz.client.HxzClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=HierarchicalOcclusionTraverser.class,remap=false)
public class HxzTraversalMixin {
    @Unique private long hxz$uniform;
    @Redirect(method="uploadUniform",at=@At(value="INVOKE",target="Lme/cortex/voxy/client/core/rendering/util/UploadStream;upload(Lme/cortex/voxy/client/core/gl/GlBuffer;JJ)J"))
    private long hxz$capture(UploadStream stream,GlBuffer buffer,long offset,long size){return hxz$uniform=stream.upload(buffer,offset,size);}
    @Inject(method="uploadUniform",at=@At("RETURN"))
    private void hxz$options(CallbackInfo ci){HxzClient.uniform(hxz$uniform);}
    @ModifyArg(method="downloadResetRequestQueue",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL45;nglClearNamedBufferSubData(IIJJIIJ)V"),index=3)
    private long hxz$clearBackgroundCounter(long original){return 8L;}
}
