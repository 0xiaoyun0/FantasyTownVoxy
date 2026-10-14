package me.cortex.voxy.hxz.core;

import java.util.Locale;

/** Identity handoff across CONFIGURATION -> PLAY, including Velocity backend switches. */
public final class SessionNamespaces {
    // VoxyClientInstance already scopes its base directory by server address (or
    // the VSS storage identity). Never put a per-connection token in a disk key:
    // servers without VSS must reopen their cache after a disconnect/restart.
    // Backends sharing an address and seed need a VSS node ID or backendOverride.
    public static final String ENDPOINT_NAMESPACE = "endpoint";
    private static String pending;
    public record Identity(String server, boolean shared, String node) {}
    private static Identity pendingIdentity;
    private static volatile long epoch;
    public static synchronized void beginConfiguration() { epoch++;pending = null; pendingIdentity = null; }
    public static long epoch(){return epoch;}
    public static synchronized void endSession(){epoch++;pending=null;pendingIdentity=null;}
    public static synchronized Identity pendingIdentity() { return pendingIdentity; }
    public static synchronized String namespaceForJoin(){return pending==null?ENDPOINT_NAMESPACE:pending;}
    public static synchronized void offer(String server, boolean shared, String node) {
        pending = null;
        pendingIdentity = null;
        if (server == null) return;
        server = server.trim().toUpperCase(Locale.ROOT);
        if (!server.matches("[0-9ABCDEFGHJKMNPQRSTVWXYZ]{8}")) return;
        node = node == null ? "" : node.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","-");
        if (node.length() > 32) node = node.substring(0,32);
        if (!shared && node.isEmpty()) return;
        pendingIdentity = new Identity(server,shared,node);
        pending = "vss:" + server.toLowerCase(Locale.ROOT)
                + (shared ? "" : "-" + (node.startsWith("node-") ? node : "node-"+node));
    }
    public static synchronized String beginJoin() {
        epoch++;
        String result = pending;
        pending = null;
        pendingIdentity = null;
        return result == null ? ENDPOINT_NAMESPACE : result;
    }
}
