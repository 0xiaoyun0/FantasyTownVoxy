import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
/** Metadata bridges and explicit, read-time VSS resource caps. Renderer bytecode is unchanged. */
public class Adapt {
 public static void main(String[] args)throws Exception{
  try(var jar=new JarFile(args[0])){for(var e:Collections.list(jar.entries())){
   String path=e.getName();
   boolean serialization=path.equals("me/cortex/voxy/common/config/Serialization.class");
   boolean options=path.equals("dev/xantha/vss/client/VSSVoxyOptionsIntegration.class");
   boolean server=path.startsWith("dev/xantha/vss/networking/server/")&&path.endsWith(".class");
   boolean tall=path.equals("dev/xantha/vss/networking/payloads/VoxelColumnS2CPayload.class")||path.equals("dev/xantha/vss/networking/client/ClientColumnProcessor.class")||path.equals("dev/xantha/vss/networking/client/ClientLodPresenceCache.class")||path.equals("dev/xantha/vss/networking/client/VSSClientNetworking.class");
   boolean loaded=path.equals("dev/xantha/vss/common/processing/LoadedColumnData.class");
   boolean predictionCache=path.equals("dev/xantha/vss/client/prediction/PredictionCacheStorage.class");
   if(!serialization&&!options&&!server&&!tall&&!loaded&&!predictionCache)continue;
   boolean[] changed={serialization||options};
   var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
   new ClassReader(jar.getInputStream(e).readAllBytes()).accept(new ClassVisitor(Opcodes.ASM9,writer){
    public MethodVisitor visitMethod(int a,String n,String d,String s,String[] ex){
     if(loaded&&n.equals("reorderSerializedSections")){
      changed[0]=true;var mv=super.visitMethod(a,n,d,s,ex);mv.visitCode();for(int i=0;i<3;i++)mv.visitVarInsn(Opcodes.ALOAD,i);mv.visitMethodInsn(Opcodes.INVOKESTATIC,"me/cortex/voxy/hxz/server/TallColumnSupport","reorder","([B[I[I)[B",false);mv.visitInsn(Opcodes.ARETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
     }
     if(path.endsWith("/VoxelColumnS2CPayload.class")&&n.equals("sanitizeReplacementSections")){
      changed[0]=true;var mv=super.visitMethod(a,n,d,s,ex);mv.visitCode();mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitMethodInsn(Opcodes.INVOKESTATIC,"me/cortex/voxy/hxz/server/TallColumnSupport","sanitize","([I)[I",false);mv.visitInsn(Opcodes.ARETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
     }
     return new MethodVisitor(Opcodes.ASM9,super.visitMethod(a,n,d,s,ex)){
     public void visitCode(){super.visitCode();
      if(predictionCache&&n.equals("forWorld")&&d.equals("(Ljava/nio/file/Path;Ljava/nio/file/Path;Ljava/lang/String;Ljava/lang/String;)Ldev/xantha/vss/client/prediction/PredictionCacheStorage;")){
       changed[0]=true;super.visitVarInsn(Opcodes.ALOAD,2);super.visitVarInsn(Opcodes.ALOAD,1);super.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/xantha/vss/client/prediction/PredictionCacheIdentity","serverScope","(Ljava/lang/String;Ljava/nio/file/Path;)Ljava/lang/String;",false);super.visitVarInsn(Opcodes.ASTORE,2);
      }
      if(path.endsWith("/state/PlayerRequestState.class")&&n.equals("setClientCapabilities")){
       changed[0]=true;super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ILOAD,1);super.visitMethodInsn(Opcodes.INVOKESTATIC,"me/cortex/voxy/hxz/server/TallColumnSupport","remember","(Ldev/xantha/vss/networking/server/state/PlayerRequestState;I)V",false);
      }
      if(path.endsWith("/server/VSSServerNetworking.class")&&n.equals("queueColumn")&&d.endsWith(";Z)Z")){
       changed[0]=true;super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,1);super.visitVarInsn(Opcodes.ALOAD,2);
       super.visitMethodInsn(Opcodes.INVOKESTATIC,"me/cortex/voxy/hxz/server/TallColumnSupport","skipLegacy","(Lnet/minecraft/server/level/ServerPlayer;Ldev/xantha/vss/networking/server/state/PlayerRequestState;Ldev/xantha/vss/networking/payloads/VoxelColumnS2CPayload;)Z",false);
       Label proceed=new Label();super.visitJumpInsn(Opcodes.IFEQ,proceed);super.visitInsn(Opcodes.ICONST_1);super.visitInsn(Opcodes.IRETURN);super.visitLabel(proceed);super.visitFrame(Opcodes.F_SAME,0,null,0,null);
      }
      int boolSlot=path.endsWith("/runtime/DiskTaskRuntime.class")&&n.equals("setUnrestrictedMode")?1:
        path.endsWith("/generation/ChunkGenerationService.class")&&n.equals("reservePackingSnapshot")&&d.endsWith("Z)J")?2:-1;
      if(boolSlot>=0){changed[0]=true;super.visitVarInsn(Opcodes.ILOAD,boolSlot);super.visitMethodInsn(Opcodes.INVOKESTATIC,"me/cortex/voxy/hxz/server/ServerResourceCaps","unrestricted","(Z)Z",false);super.visitVarInsn(Opcodes.ISTORE,boolSlot);}
     }
     public void visitIntInsn(int op,int operand){
      boolean sectionLimit=path.endsWith("/VoxelColumnS2CPayload.class")&&n.equals("decode")
       ||path.endsWith("/ColumnPayloadSplitter.class")&&n.equals("readSections")
       ||path.endsWith("/PersistentColumnLodStore.class")&&(n.equals("validSectionManifest")||n.equals("readSectionManifest"))
       ||path.endsWith("/ClientColumnProcessor.class")&&n.equals("decodeColumn")
       ||path.endsWith("/ClientLodPresenceCache.class")&&(n.equals("ensureLoaded")||n.equals("encodeSectionManifest"));
      if(operand==64&&sectionLimit){changed[0]=true;super.visitIntInsn(Opcodes.SIPUSH,255);return;}
      super.visitIntInsn(op,operand);
     }
     public void visitInsn(int op){
      if(op==Opcodes.LRETURN&&path.endsWith("/state/PlayerRequestState.class")&&n.equals("desiredBandwidth")){
       changed[0]=true;super.visitMethodInsn(Opcodes.INVOKESTATIC,"me/cortex/voxy/hxz/server/ServerDownloads","personalRate","(J)J",false);
      }
      if(predictionCache&&n.equals("directory")&&op==Opcodes.ARETURN){changed[0]=true;super.visitVarInsn(Opcodes.ALOAD,1);super.visitMethodInsn(Opcodes.INVOKESTATIC,"dev/xantha/vss/client/prediction/PredictionCacheIdentity","directory","(Ljava/nio/file/Path;Ldev/xantha/vss/client/prediction/ClientTerrainSampler;)Ljava/nio/file/Path;",false);}
      if(op==Opcodes.IRETURN&&path.endsWith("/client/VSSClientNetworking.class")&&n.equals("clientCapabilities")){
       changed[0]=true;super.visitIntInsn(Opcodes.BIPUSH,32);super.visitInsn(Opcodes.IOR);
      }
      super.visitInsn(op);
     }
     public void visitFieldInsn(int op,String owner,String name,String desc){
      super.visitFieldInsn(op,owner,name,desc);
      if(server&&op==Opcodes.GETFIELD&&owner.equals("dev/xantha/vss/config/VSSServerConfig")){
       changed[0]=true;
       if(desc.equals("I")){super.visitLdcInsn(name);super.visitMethodInsn(Opcodes.INVOKESTATIC,"me/cortex/voxy/hxz/server/ServerResourceCaps","cap","(ILjava/lang/String;)I",false);}
       if(desc.equals("Z")&&name.equals("enableChunkGeneration"))super.visitMethodInsn(Opcodes.INVOKESTATIC,"me/cortex/voxy/hxz/server/ServerResourceCaps","generation","(Z)Z",false);
       if(desc.equals("Z")&&name.equals("enabled")&&path.endsWith("/session/PlayerSessionManager.class"))super.visitMethodInsn(Opcodes.INVOKESTATIC,"me/cortex/voxy/hxz/server/ServerDownloads","sharing","(Z)Z",false);
      }
     }
     public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){
      if(predictionCache&&owner.equals("dev/xantha/vss/client/prediction/PredictionCacheMigration")&&name.equals("find")){
       changed[0]=true;owner="dev/xantha/vss/client/prediction/PredictionCacheIdentity";name="findLegacy";
      }
      super.visitMethodInsn(op,owner,name,desc,itf);
      if(server&&owner.equals("dev/xantha/vss/config/VSSServerConfig")&&name.startsWith("automaticGeneration")&&desc.equals("()I")){
       changed[0]=true;
       super.visitLdcInsn(name);super.visitMethodInsn(Opcodes.INVOKESTATIC,"me/cortex/voxy/hxz/server/ServerResourceCaps","cap","(ILjava/lang/String;)I",false);
      }
     }
     public void visitLdcInsn(Object v){
      if(serialization&&"neovoxy".equals(v)||options&&n.equals("modVersion")&&"vss".equals(v))v="voxy";
      if(path.endsWith("/generation/ChunkGenerationService.class")&&n.equals("tick")&&Long.valueOf(Long.MAX_VALUE).equals(v)){
       changed[0]=true;
       super.visitMethodInsn(Opcodes.INVOKESTATIC,"me/cortex/voxy/hxz/server/ServerTickGuard","generationDeadline","()J",false);return;
      }
      super.visitLdcInsn(v);
     }
    };}
   },0);
   if(!changed[0])continue;
   Path out=Path.of(args[1]).resolve(path);Files.createDirectories(out.getParent());Files.write(out,writer.toByteArray());
  }}
 }
}
