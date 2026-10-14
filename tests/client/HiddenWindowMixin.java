package hxz.smoke.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.Window;
/** Hide only this test process's render window; rendering still uses the real GPU. */
@Mixin(Window.class)
public class HiddenWindowMixin {
    @Redirect(method="<init>",at=@At(value="INVOKE",target="Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"),require=0)
    private long hxz$hidden(int width,int height,CharSequence title,long monitor,long share) {
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        return GLFW.glfwCreateWindow(width,height,title,monitor,share);
    }
    // NeoForge can supply a pre-created window without calling glfwCreateWindow here.
    @Inject(method="<init>",at=@At("RETURN"))
    private void hxz$hideProvidedWindow(CallbackInfo ci) {
        GLFW.glfwHideWindow(((Window)(Object)this).getWindow());
    }
}
