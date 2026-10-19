import java.nio.file.*;
import java.util.jar.JarFile;
import org.objectweb.asm.*;

/** Applies the exact two production redirect call sites for a headless manager contract. */
public final class PressureRedirectFixture {
    public static void main(String[] args)throws Exception {
        String owner="dev/xantha/vss/client/prediction/PredictionTileManager";
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);int[] count={0};
        try(var jar=new JarFile(args[0])) {
            new ClassReader(jar.getInputStream(jar.getJarEntry(owner+".class"))).accept(new ClassVisitor(Opcodes.ASM9,writer) {
                public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e) {
                    var mv=super.visitMethod(a,n,d,s,e);
                    if(!n.equals("pruneReady"))return mv;
                    return new MethodVisitor(Opcodes.ASM9,mv) {
                        public void visitMethodInsn(int op,String o,String name,String desc,boolean iface) {
                            if(name.equals("coveredByAncestor")&&o.equals("dev/xantha/vss/client/prediction/PredictionTileResidencyPolicy")) {
                                for(String field:new String[]{"ready","desiredKeys","pending","cameraBlockX","cameraBlockZ"}) {
                                    super.visitVarInsn(Opcodes.ALOAD,0);
                                    super.visitFieldInsn(Opcodes.GETFIELD,owner,field,field.equals("ready")?"Ljava/util/Map;":field.startsWith("camera")?"D":"Ljava/util/Set;");
                                }
                                super.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/xantha/vss/client/prediction/PredictionPressureRetirement","coveredOrObsolete",desc.substring(0,desc.length()-2)+"Ljava/util/Map;Ljava/util/Set;Ljava/util/Set;DD)Z",false);count[0]++;
                            } else super.visitMethodInsn(op,o,name,desc,iface);
                        }
                    };
                }
            },0);
        }
        if(count[0]!=2)throw new AssertionError("Expected exactly two pressure retirement call sites, got "+count[0]);
        var file=Path.of(args[1],owner+".class");Files.createDirectories(file.getParent());Files.write(file,writer.toByteArray());
    }
}
