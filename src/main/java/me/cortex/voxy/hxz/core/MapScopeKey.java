package me.cortex.voxy.hxz.core;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
public final class MapScopeKey {
    public static String root(String address,String backend,long biomeSeed,boolean realm){
        String value=address.trim().toLowerCase(Locale.ROOT)+"\n"+backend+"\n"+biomeSeed;
        return (realm?"Realms_ftv_":"Multiplayer_ftv_")+TerrainFingerprint.digest("xaero-storage-v1",value.getBytes(StandardCharsets.UTF_8)).substring(0,32);
    }
}
