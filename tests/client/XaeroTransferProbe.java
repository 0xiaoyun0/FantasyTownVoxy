package hxz.smoke;
import net.minecraft.client.Minecraft;
import me.cortex.voxy.commonImpl.WorldIdentifier;

/** Real Velocity transfer, with identically seeded backend worlds. */
public final class XaeroTransferProbe {
    private static int phase,ticks;
    private static String aRoot,aLod,bRoot;
    private static long seed;
    private static Object connection;
    private static Object world()throws Exception {
        Object session=Class.forName("xaero.map.WorldMapSession").getMethod("getCurrentSession").invoke(null);
        Object processor=session.getClass().getMethod("getMapProcessor").invoke(session);
        return processor.getClass().getMethod("getMapWorld").invoke(processor);
    }
    private static void mark(String value)throws Exception {Object world=world();world.getClass().getMethod("setPlayerTeleportCommandFormat",String.class).invoke(world,value);world.getClass().getMethod("saveConfig").invoke(world);}
    public static boolean tick()throws Exception {
        var mc=Minecraft.getInstance();var id=WorldIdentifier.of(mc.level);
        if(++ticks>2400)throw new AssertionError("Velocity transfer timed out phase "+phase+" backend="+id.backendNamespace);
        if(phase==0){
            if(!id.backendNamespace.endsWith("node-alpha"))throw new AssertionError("Initial server did not advertise alpha identity: "+id.backendNamespace);
            XaeroStorageProbe.run();aRoot=XaeroStorageProbe.root();aLod=id.getWorldId();seed=id.biomeSeed;mark("ftv-alpha-fixture");
            connection=mc.getConnection();mc.player.connection.sendCommand("server beta");phase++;ticks=0;return false;
        }
        if(mc.getConnection()==connection)return false;
        if(phase==1){
            if(!id.backendNamespace.endsWith("node-beta")||id.biomeSeed!=seed)throw new AssertionError("Fixture must transfer to beta with SAME seed: "+id.backendNamespace);
            XaeroStorageProbe.run();bRoot=XaeroStorageProbe.root();if(bRoot.equals(aRoot)||id.getWorldId().equals(aLod))throw new AssertionError("B reused A map/LOD storage");
            Object world=world();if(world.getClass().getMethod("getPlayerTeleportCommandFormat").invoke(world).equals("ftv-alpha-fixture"))throw new AssertionError("B read A map configuration");
            mark("ftv-beta-fixture");connection=mc.getConnection();mc.player.connection.sendCommand("server alpha");phase++;ticks=0;return false;
        }
        if(!id.backendNamespace.endsWith("node-alpha")||!id.getWorldId().equals(aLod)||!XaeroStorageProbe.root().equals(aRoot))throw new AssertionError("Returning to A failed to reuse its own map and LOD");
        Object world=world();if(!world.getClass().getMethod("getPlayerTeleportCommandFormat").invoke(world).equals("ftv-alpha-fixture"))throw new AssertionError("B overwrote A saved Xaero map config");
        System.out.println("HXZ_VELOCITY PASS real same-endpoint SAME-SEED alpha -> beta -> alpha; distinct Xaero world/minimap roots and LOD databases; A's saved map config survives B: "+aRoot+" / "+bRoot);
        return true;
    }
}
