import java.nio.file.*;
import dev.xantha.vss.client.prediction.PredictionNormalDepthBridge;

/** Exercise the final shader actually shipped by NeoVoxy, not a synthetic Voxy version. */
public class PredictionDepthContractTest {
    public static void main(String[] args)throws Exception{
        String path="voxy:post/blit_texture_depth_cutout.frag";
        String source=Files.readString(Path.of(args[0]));
        String patched=PredictionNormalDepthBridge.patch(path,source);
        if(!patched.contains("uniform bool VssPredictionBeforeWater"))
            throw new AssertionError("NeoVoxy final blit lacks prediction depth/water protection; VSS silently disables ordering");
        if(!patched.equals(PredictionNormalDepthBridge.patch(path,patched)))throw new AssertionError("Patch is not idempotent");
        if(!source.equals(PredictionNormalDepthBridge.patch("other:shader",source)))throw new AssertionError("Unrelated shader changed");
        if(!"unknown layout".equals(PredictionNormalDepthBridge.patch(path,"unknown layout")))throw new AssertionError("Unknown shader changed");
        Files.writeString(Path.of(args[1]),patched);
        System.out.println("PASS real NeoVoxy final blit depth/water contract, repeat patch, unknown shader fallback");
    }
}
