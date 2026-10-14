package hxz.smoke;

import java.nio.file.*;
import java.util.*;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.commonImpl.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;

/** Two different JVMs: capture real terrain, then read it with all ingestion disabled. */
public final class PersistenceProbe {
    private static int ticks;
    public static boolean tick()throws Exception {
        var mc=Minecraft.getInstance();var id=WorldIdentifier.of(mc.level);
        var engine=id.getOrCreateEngine();
        String mode=System.getProperty("hxz.smoke.persistence");
        Path file=Path.of(System.getProperty("hxz.smoke.dir"),"persisted-terrain.properties");
        var state=new Properties();
        boolean shared=Boolean.getBoolean("hxz.smoke.expectedSharing");
        int x=shared?0:mc.player.getBlockX(),z=shared?128:mc.player.getBlockZ(),y=-61;
        if(mode.equals("read")) {
            try(var in=Files.newInputStream(file)){state.load(in);}
            if(!id.getWorldId().equals(state.getProperty("world")))throw new AssertionError("Rejoin selected a different database: "+state.getProperty("world")+" -> "+id.getWorldId());
            if(me.cortex.voxy.client.config.VoxyConfig.CONFIG.ingestEnabled||dev.xantha.vss.config.VSSClientConfig.CONFIG.receiveServerLods||dev.xantha.vss.config.VSSClientConfig.CONFIG.enablePrediction)
                throw new AssertionError("Reader must not regenerate or redownload the test terrain");
            x=Integer.parseInt(state.getProperty("x"));y=Integer.parseInt(state.getProperty("y"));z=Integer.parseInt(state.getProperty("z"));
            if(Boolean.getBoolean("hxz.smoke.xaero")&&!XaeroStorageProbe.root().equals(state.getProperty("xaeroRoot")))throw new AssertionError("Xaero changed disk scope on JVM restart");
        }
        if(mode.equals("write")&&shared&&HxzSmokeProbe.farColumns==0){if(++ticks>1600)throw new AssertionError("No remote LOD column received");return false;}
        var section=engine.acquireIfExists(0,x>>5,y>>5,z>>5);
        if(section==null){if(mode.equals("read")||++ticks>1600)throw new AssertionError("Persisted terrain section is missing");return false;}
        String block;
        try {
            long value=section._unsafeGetRawDataArray()[((y&31)<<10)|((z&31)<<5)|(x&31)];
            if(Mapper.isAir(value)){if(mode.equals("read")||++ticks>1600)throw new AssertionError("Persisted terrain became air at "+x+","+y+","+z+"; actual="+mc.level.getBlockState(new net.minecraft.core.BlockPos(x,y,z)));return false;}
            block=BuiltInRegistries.BLOCK.getKey(engine.getMapper().getBlockStateFromBlockId(Mapper.getBlockId(value)).getBlock()).toString();
        }finally{section.release();}
        // The renderer depends on parent LODs as well as full-resolution terrain.
        var parent=engine.acquireIfExists(4,x>>9,y>>9,z>>9);
        if(parent==null){if(mode.equals("read")||++ticks>1600)throw new AssertionError("LOD parent was not persisted");return false;}
        parent.release();
        if(mode.equals("write")) {
            state.setProperty("world",id.getWorldId());state.setProperty("namespace",id.backendNamespace);
            if(Boolean.getBoolean("hxz.smoke.xaero"))state.setProperty("xaeroRoot",XaeroStorageProbe.root());
            state.setProperty("x",""+x);state.setProperty("y",""+y);state.setProperty("z",""+z);state.setProperty("block",block);
            try(var out=Files.newOutputStream(file)){state.store(out,"Private persistence fixture only");}
            System.out.println("HXZ_PERSISTENCE PASS write actual "+(shared?"server-shared remote LOD":"vanilla-observed terrain")+", world="+id.getWorldId()+", block="+block);
        }else {
            if(!block.equals(state.getProperty("block")))throw new AssertionError("Saved block palette changed: "+state.getProperty("block")+" -> "+block);
            System.out.println("HXZ_PERSISTENCE PASS read same terrain, block mapping and parent LOD after JVM restart, with ingestion/sharing/prediction disabled; world="+id.getWorldId());
        }
        return true;
    }
}
