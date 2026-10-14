package hxz.safetyprobe;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.core.registries.Registries;
import dev.xantha.vss.networking.payloads.VoxelColumnS2CPayload;
import dev.xantha.vss.common.processing.EncodedColumnData;
import dev.xantha.vss.networking.server.sending.ColumnPayloadSplitter;
import io.netty.buffer.Unpooled;

/** Commands are intentionally fixture-only. Never included in a distribution. */
@Mod("hxz_feature_probe")
public final class FeatureServerProbe {
    public FeatureServerProbe(){NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class,event->{
        var root=Commands.literal("hxzfeature");
        root.then(Commands.literal("op").executes(c->{c.getSource().getServer().getPlayerList().op(c.getSource().getPlayerOrException().getGameProfile());return 1;}));
        root.then(Commands.literal("deop").executes(c->{c.getSource().getServer().getPlayerList().deop(c.getSource().getPlayerOrException().getGameProfile());return 1;}));
        root.then(Commands.literal("tall").executes(c->{
            try{test(c.getSource().getLevel());return 1;}catch(Exception e){throw new RuntimeException(e);}
        }));event.getDispatcher().register(root);
    });}
    private static void test(net.minecraft.server.level.ServerLevel level)throws Exception {
        ProfileTransferProbe.run();
        for(int count:new int[]{64,65,68,128,255}){
            FriendlyByteBuf body=new FriendlyByteBuf(Unpooled.buffer());int[] ys=new int[count],lengths=new int[count];body.writeVarInt(count);
            for(int i=0;i<count;i++){
                ys[i]=i-128;int start=body.writerIndex();body.writeByte(ys[i]);var section=new LevelChunkSection(level.registryAccess().registryOrThrow(Registries.BIOME));section.write(body);body.writeBoolean(false).writeBoolean(false);lengths[i]=body.writerIndex()-start;
            }
            byte[] raw=new byte[body.readableBytes()];body.readBytes(raw);body.release();
            var data=new EncodedColumnData(0,0,0,raw.length,raw,1L,5,true,ys,lengths,EncodedColumnData.crc32c(raw));
            var payload=new VoxelColumnS2CPayload(91,level.dimension(),data).withTransferMetadata(3,0,1,ys);
            var parts=ColumnPayloadSplitter.splitForBandwidth(level,payload,62500,false);int kept=0;
            for(var part:parts){
                var wire=new FriendlyByteBuf(Unpooled.buffer());VoxelColumnS2CPayload.encode(part,wire);var decoded=VoxelColumnS2CPayload.decode(wire);wire.release();
                var decodedBody=new FriendlyByteBuf(Unpooled.wrappedBuffer(decoded.decompressedSections()));kept+=decodedBody.readVarInt();decodedBody.release();
            }
            if(kept!=count||parts.getLast().replacementSectionYs().length!=count)throw new AssertionError("Tall section truncation "+count);
            var config=new dev.xantha.vss.config.VSSServerConfig();config.enablePersistentColumnCache=true;
            var store=new dev.xantha.vss.networking.server.storage.PersistentColumnLodStore(config);
            var stored=EncodedColumnData.encode(new dev.xantha.vss.common.processing.LoadedColumnData(-40000-count,0,raw,raw.length,true,ys,lengths),System.currentTimeMillis());
            if(!store.writeConfirmed(level.getServer(),level.dimension(),stored))throw new AssertionError("Tall column cache write failed "+count);
            store.clearMemory();var restored=store.read(level.getServer(),level.dimension(),stored.chunkX(),0,0);
            if(restored==null||restored.columnData().sectionYs().length!=count||!java.util.Arrays.equals(stored.encodedBytes(),restored.columnData().encodedBytes()))throw new AssertionError("Tall column cache reload failed "+count);
        }
        var state=new dev.xantha.vss.networking.server.state.PlayerRequestState();state.setClientCapabilities(1);
        if(me.cortex.voxy.hxz.server.TallColumnSupport.supports(state))throw new AssertionError("Legacy client incorrectly supported");state.setClientCapabilities(33);
        if(!me.cortex.voxy.hxz.server.TallColumnSupport.supports(state))throw new AssertionError("Capability negotiation missing");
        System.out.println("HXZ_TALL PASS 64/65/68/128/255 sections through encoder, splitter, wire decoder, disk-cache reload and client capability negotiation");
        var safetyFile=java.nio.file.Path.of("config/voxy-hxz-server.json");var vssFile=java.nio.file.Path.of("config/vss-server-config.json");
        byte[] safetyBytes=java.nio.file.Files.readAllBytes(safetyFile),vssBytes=java.nio.file.Files.readAllBytes(vssFile);
        try{
            long oldRevision=me.cortex.voxy.hxz.server.ServerPanel.snapshot("").get("revision").getAsLong();
            var changed=com.google.gson.JsonParser.parseString(new String(safetyBytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();changed.addProperty("columnsPerTick",7);
            java.nio.file.Files.writeString(safetyFile,changed.toString());me.cortex.voxy.hxz.server.ServerPanel.reload(level.getServer());
            if(me.cortex.voxy.hxz.server.ServerSafetyConfig.CONFIG.columnsPerTick!=7)throw new AssertionError("Server hot reload not applied");
            var values=new com.google.gson.JsonObject();values.addProperty("safety.columnsPerTick","8");
            try{me.cortex.voxy.hxz.server.ServerPanel.apply(level.getServer(),values,oldRevision);throw new AssertionError("Stale admin revision accepted");}catch(IllegalArgumentException expected){}
            java.nio.file.Files.writeString(vssFile,"{broken-json");
            try{me.cortex.voxy.hxz.server.ServerPanel.reload(level.getServer());throw new AssertionError("Invalid server config accepted");}catch(com.google.gson.JsonParseException expected){}
            if(me.cortex.voxy.hxz.server.ServerSafetyConfig.CONFIG.columnsPerTick!=7)throw new AssertionError("Invalid reload replaced live server config");
        }finally{java.nio.file.Files.write(safetyFile,safetyBytes);java.nio.file.Files.write(vssFile,vssBytes);me.cortex.voxy.hxz.server.ServerPanel.reload(level.getServer());}
        System.out.println("HXZ_RELOAD PASS server hot reload, stale admin revision rejection and malformed-config rollback");
    }
}
