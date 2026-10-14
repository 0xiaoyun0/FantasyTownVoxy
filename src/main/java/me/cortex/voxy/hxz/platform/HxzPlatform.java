package me.cortex.voxy.hxz.platform;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;
import java.nio.file.Path;
import java.util.List;

/** Native loader seam; it is available without Fabric/Connector on a server. */
public final class HxzPlatform {
    private static final HxzPlatform INSTANCE = new HxzPlatform();
    public static HxzPlatform getInstance() { return INSTANCE; }
    public boolean isModLoaded(String id) {
        var list=LoadingModList.get();
        return list!=null && list.getModFileById(id)!=null;
    }
    public Path getConfigDir() { return FMLPaths.CONFIGDIR.get(); }
    public static Path modRoot() {
        var list=LoadingModList.get();
        var file=list==null?null:list.getModFileById("voxy");
        return file==null?null:file.getFile().findResource("");
    }
    public static String version() {
        var list=LoadingModList.get();
        if(list==null) return "0.2.15-hxz.2";
        return list.getMods().stream().filter(m->m.getModId().equals("voxy"))
                .map(m->m.getVersion().toString()).findFirst().orElse("0.2.15-hxz.2");
    }
    public static boolean dedicatedServer() { return FMLEnvironment.dist.isDedicatedServer(); }
    /** Retain optional FREX interop when a user already has Connector installed. */
    @SuppressWarnings("unchecked")
    public <T> List<T> getEntrypoints(String key, Class<T> type) {
        try {
            var api=Class.forName("net.fabricmc.loader.api.FabricLoader");
            var loader=api.getMethod("getInstance").invoke(null);
            return (List<T>)api.getMethod("getEntrypoints",String.class,Class.class).invoke(loader,key,type);
        } catch (ClassNotFoundException absent) { return List.of(); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Optional entrypoint lookup failed",failure); }
    }
}
