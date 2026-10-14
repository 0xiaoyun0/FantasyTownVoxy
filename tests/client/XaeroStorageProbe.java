package hxz.smoke;
import java.lang.reflect.*;
import java.nio.file.*;
import me.cortex.voxy.hxz.client.XaeroMapScopes;
import me.cortex.voxy.hxz.core.MapScopeKey;
import net.minecraft.client.Minecraft;

public final class XaeroStorageProbe {
    public static String root()throws Exception {
        Object map=Class.forName("xaero.map.WorldMapSession").getMethod("getCurrentSession").invoke(null);
        Object processor=map.getClass().getMethod("getMapProcessor").invoke(map);
        Object world=processor.getClass().getMethod("getMapWorld").invoke(processor);
        String root=(String)world.getClass().getMethod("getMainId").invoke(world);
        Object module=Class.forName("xaero.hud.minimap.BuiltInHudModules").getField("MINIMAP").get(null);
        Object session=module.getClass().getMethod("getCurrentSession").invoke(module);
        Object state=session.getClass().getMethod("getWorldState").invoke(session);
        Object path=state.getClass().getMethod("getAutoRootContainerPath").invoke(state);
        String minimap=(String)path.getClass().getMethod("getLastNode").invoke(path);
        if(!root.equals(minimap))throw new AssertionError("World map and minimap use different backend scopes: "+root+" / "+minimap);
        return root;
    }
    public static void run()throws Exception {
        var mc=Minecraft.getInstance();var id=me.cortex.voxy.commonImpl.WorldIdentifier.of(mc.level);var server=mc.getConnection().getServerData();
        String actual=root(),expected=MapScopeKey.root(server.ip,id.backendNamespace,id.biomeSeed,server.isRealm());
        if(!actual.equals(expected)||!actual.equals(XaeroMapScopes.root("",mc.getConnection())))throw new AssertionError("Xaero initialized before backend scope: "+actual+" expected "+expected);
        String a=MapScopeKey.root(server.ip,"vss:1234abcd-node-a",id.biomeSeed,false),b=MapScopeKey.root(server.ip,"vss:1234abcd-node-b",id.biomeSeed,false);
        if(a.equals(b)||!a.equals(MapScopeKey.root(server.ip,"vss:1234abcd-node-a",id.biomeSeed,false)))throw new AssertionError("A/B/A scope collision");
        Method folder=Class.forName("xaero.map.file.MapSaveLoad").getMethod("getRootFolder",String.class);
        Path pathA=(Path)folder.invoke(null,a),pathB=(Path)folder.invoke(null,b);
        if(pathA.equals(pathB))throw new AssertionError("Xaero disk roots collide");
        Files.createDirectories(pathA);Files.createDirectories(pathB);
        Files.writeString(pathA.resolve("hxz-fixture-marker.txt"),"a");Files.writeString(pathB.resolve("hxz-fixture-marker.txt"),"b");
        if(!Files.readString(pathA.resolve("hxz-fixture-marker.txt")).equals("a"))throw new AssertionError("Backend B overwrote backend A");
        System.out.println("HXZ_XAERO PASS actual world-map/minimap session roots agree with server identity before map I/O; same-address same-seed A/B/A disk roots isolate and reuse: "+actual);
    }
}
