package me.cortex.voxy.hxz.client;
import java.util.*;
import me.cortex.voxy.hxz.HxzSettings;
import me.cortex.voxy.hxz.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Freeze each Xaero session's disk scope before its map or waypoint I/O starts. */
public final class XaeroMapScopes {
    private static final Map<ClientPacketListener,String> scopes=Collections.synchronizedMap(new WeakHashMap<>());
    public static void capture(ClientPacketListener listener,long biomeSeed){
        var settings=HxzSettings.CONFIG;var server=listener.getServerData();
        if(!settings.xaeroMapIsolation||Minecraft.getInstance().getSingleplayerServer()!=null||server==null){scopes.remove(listener);return;}
        String backend=settings.backendOverride.isBlank()?SessionNamespaces.namespaceForJoin():"manual:"+settings.backendOverride;
        scopes.put(listener,MapScopeKey.root(server.ip,backend,biomeSeed,server.isRealm()));
    }
    public static String root(String original,ClientPacketListener listener){return scopes.getOrDefault(listener,original);}
}
