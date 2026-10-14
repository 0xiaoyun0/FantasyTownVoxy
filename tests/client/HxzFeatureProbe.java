package hxz.smoke;

import java.nio.*;
import java.nio.file.*;
import java.util.*;
import me.cortex.voxy.hxz.HxzSettings;
import me.cortex.voxy.hxz.client.*;
import me.cortex.voxy.hxz.importer.*;
import me.cortex.voxy.commonImpl.*;
import me.cortex.voxy.common.world.*;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;
import org.rocksdb.*;

/** Runs only in the private fixture; verifies real storage, live renderer and remote panel. */
public final class HxzFeatureProbe {
    private static int phase,ticks;
    private static Path source;
    private static byte[] originalConfig;
    private static String initialMode;
    private static int pendingDistance;
    public static boolean tick()throws Exception{
        var mc=Minecraft.getInstance();ticks++;
        if(ticks>1500)throw new AssertionError("Feature test timed out at phase "+phase+": "+AdminClient.message+" | "+LodImportJob.status);
        switch(phase){
            case 0->{
                var info=net.neoforged.fml.ModList.get().getModContainerById("voxy").orElseThrow().getModInfo();
                if(!info.getDisplayName().equals("Fantasy Town Voxy")||!info.getVersion().toString().equals(System.getProperty("hxz.smoke.version")))throw new AssertionError("Release identity mismatch");
                if(!new HxzSettings().originalRendering)throw new AssertionError("Default is not NeoVoxy");
                tallClient();
                OptimizationProbe.run();
                var all=ClientOptions.all();var ids=new HashSet<String>();for(var o:all)if(!ids.add(o.id()))throw new AssertionError("Duplicate option "+o.id());
                for(var f:dev.xantha.vss.config.VSSClientConfig.class.getFields()){
                    if(java.lang.reflect.Modifier.isStatic(f.getModifiers())||f.isAnnotationPresent(Deprecated.class)||f.getName().equals("configVersion"))continue;
                    if(!ids.contains("vss_"+f.getName().toLowerCase(Locale.ROOT))&&!f.getName().equals("receiveServerLods"))throw new AssertionError("Client option omitted: "+f.getName());
                }
                source=fixture();var engine=WorldIdentifier.ofEngine(mc.level);
                if(!VoxyCommon.getInstance().getImportManager().makeAndRunIfNone(engine,()->new LodImportJob(source,engine)))throw new AssertionError("Import not started");
                phase++;ticks=0;
            }
            case 1->{if(LodImportJob.active!=null)return false;
                if(!LodImportJob.status.startsWith("导入完成"))throw new AssertionError(LodImportJob.status);
                var engine=WorldIdentifier.ofEngine(mc.level);var section=engine.acquireIfExists(0,32,0,32);
                if(section==null)throw new AssertionError("Imported section missing");
                try{long data=section._unsafeGetRawDataArray()[0];if(engine.getMapper().getBlockStateFromBlockId(Mapper.getBlockId(data)).getBlock()!=Blocks.DIAMOND_BLOCK)throw new AssertionError("Imported source ID was not remapped");}
                finally{section.release();}
                var parent=engine.acquireIfExists(4,2,0,2);if(parent==null)throw new AssertionError("Import did not build parent LOD levels");parent.release();
                System.out.println("HXZ_FEATURE PASS legacy database import, palette remap and LOD parent rebuild");
                initialMode=HxzSettings.CONFIG.originalRendering?"neovoxy":"hxz";
                var mode=ClientOptions.all().stream().filter(o->o.id().equals("hxz_originalrendering")).findFirst().orElseThrow();mode.write().accept(initialMode.equals("hxz")?"neovoxy":"hxz");LiveClientConfig.save();
                phase++;ticks=0;
            }
            case 2->{if(ticks<45)return false;
                if(!LiveClientConfig.message.equals("已应用并保存。")||VoxyCommon.getInstance()==null)throw new AssertionError("Hot renderer rebuild failed: "+LiveClientConfig.message);
                var settings=Path.of("config/voxy-hxz-neovoxy.json");originalConfig=Files.readAllBytes(settings);Files.writeString(settings,"{invalid-json");boolean old=HxzSettings.CONFIG.originalRendering;LiveClientConfig.reload();
                if(HxzSettings.CONFIG.originalRendering!=old||!LiveClientConfig.message.startsWith("重载失败"))throw new AssertionError("Invalid reload replaced live config");Files.write(settings,originalConfig);
                LiveClientConfig.reload();System.out.println("HXZ_FEATURE PASS live render-mode switch and invalid-config rollback");
                mc.player.connection.sendCommand("hxzfeature deop");phase++;ticks=0;
            }
            case 3->{if(ticks<20)return false;AdminClient.request("set",Map.of("safety.columnsPerTick","9"));phase++;ticks=0;}
            case 4->{if(ticks<30)return false;if(AdminClient.allowed||!AdminClient.message.contains("权限"))throw new AssertionError("Non-OP admin request was accepted: "+AdminClient.message);System.out.println("HXZ_FEATURE PASS non-OP configuration mutation denied");mc.player.connection.sendCommand("hxzfeature op");phase++;ticks=0;}
            case 5->{if(ticks<20)return false;AdminClient.request("get",Map.of());phase++;ticks=0;}
            case 6->{if(!AdminClient.allowed||AdminClient.options().isEmpty())return false;
                var option=AdminClient.options().stream().filter(o->o.id().equals("safety.columnsPerTick")).findFirst().orElseThrow();if(!option.read().get().equals(16))throw new AssertionError("Denied operation mutated server");
                if(ticks<20)return false;
                mc.setScreen(SodiumPanel.createScreen(null));
                if(!(mc.screen instanceof net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen))throw new AssertionError("Entry point did not open native Sodium");
                for(var mod:net.caffeinemc.mods.sodium.client.config.ConfigManager.CONFIG.getModOptions())if(mod.configId().equals("voxy")){
                    if(mod.pages().size()!=14)throw new AssertionError("Missing native pages: "+mod.pages().size());
                    for(var page:mod.pages())if(!(page instanceof net.caffeinemc.mods.sodium.client.config.structure.OptionPage))throw new AssertionError("Standalone page still registered");
                }
                integer("admin_safety.columnspertick").modifyValue(8);integer("admin_safety.columnsperplayertick").modifyValue(3);
                net.caffeinemc.mods.sodium.client.config.ConfigManager.CONFIG.applyAllOptions();
                if(!AdminClient.pending)throw new AssertionError("Native Apply did not batch server changes");
                pendingDistance=integer("voxy_sectionrenderdistance").getAppliedValue()+1;
                integer("voxy_sectionrenderdistance").modifyValue(pendingDistance);
                phase++;ticks=0;
            }
            case 7->{if(ticks<30)return false;var option=AdminClient.options().stream().filter(o->o.id().equals("safety.columnsPerTick")).findFirst().orElseThrow();if(!option.read().get().equals(8))throw new AssertionError("OP panel did not apply config");
                if(integer("admin_safety.columnspertick").getAppliedValue()!=8||integer("admin_safety.columnsperplayertick").getAppliedValue()!=3)throw new AssertionError("Native server bindings not refreshed");
                if(integer("voxy_sectionrenderdistance").getValidatedValue()!=pendingDistance||!integer("voxy_sectionrenderdistance").hasChanged())throw new AssertionError("Server response discarded client draft");
                integer("voxy_sectionrenderdistance").modifyValue(integer("voxy_sectionrenderdistance").getAppliedValue());
                integer("admin_safety.columnspertick").modifyValue(16);integer("admin_safety.columnsperplayertick").modifyValue(4);
                net.caffeinemc.mods.sodium.client.config.ConfigManager.CONFIG.applyAllOptions();
                System.out.println("HXZ_FEATURE PASS native Sodium OP Apply batching, authoritative roundtrip and client draft preservation");
                phase++;ticks=0;
            }
            case 8->{if(ticks<20)return false;screenshot("fantasy-town-voxy-sodium.png");((net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen)mc.screen).jumpToPage(SodiumPanel.page("管理员 · 负载保护"));phase++;ticks=0;}
            case 9->{if(ticks<25)return false;screenshot("fantasy-town-voxy-admin.png");((net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen)mc.screen).jumpToPage(SodiumPanel.page("导入 LOD"));phase++;ticks=0;}
            case 10->{if(ticks<20)return false;screenshot("fantasy-town-voxy-import.png");
                var path=(net.caffeinemc.mods.sodium.client.config.structure.ExternalButtonOption)net.caffeinemc.mods.sodium.client.config.ConfigManager.CONFIG.getOption(SodiumPanel.id("import_path"));
                path.getCurrentScreenConsumer().accept(mc.screen);
                if(!(((net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen)mc.screen).getPrompt() instanceof SodiumPrompt))throw new AssertionError("Import input left Sodium");
                ImportActions.importPath="";
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.CharacterTyped.Pre(mc.screen,'X',0));
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre(mc.screen,257,0,0));
                if(!ImportActions.importPath.equals("X"))throw new AssertionError("Sodium text input or key dispatch failed");
                phase++;ticks=0;}
            case 11->{if(ticks<10)return false;
                boolean visual=HxzSettings.CONFIG.originalRendering;int distance=integer("voxy_sectionrenderdistance").getAppliedValue();
                var admin=integer("admin_safety.columnspertick");admin.modifyValue(15);
                var preset=(net.caffeinemc.mods.sodium.client.config.structure.ExternalButtonOption)net.caffeinemc.mods.sodium.client.config.ConfigManager.CONFIG.getOption(SodiumPanel.id("performance_balanced"));
                preset.getCurrentScreenConsumer().accept(mc.screen);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre(mc.screen,257,0,0));
                if(!HxzSettings.CONFIG.performanceScheduling||HxzSettings.CONFIG.originalRendering!=visual||integer("voxy_sectionrenderdistance").getAppliedValue()!=distance||admin.getValidatedValue()!=15||!admin.hasChanged())throw new AssertionError("Preset changed visual/distance/admin draft");
                admin.modifyValue(admin.getAppliedValue());
                System.out.println("HXZ_OPTIMIZATION PASS native Sodium performance-preset confirmation preserves visual mode, maximum distance and admin draft");
                ((net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen)mc.screen).jumpToPage(SodiumPanel.page("基本显示"));
                phase++;ticks=0;}
            case 12->{if(ticks<10)return false;var screen=(net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen)mc.screen;
                boolean found=false;
                for(var widget:screen.children())if(widget instanceof net.caffeinemc.mods.sodium.client.gui.options.control.AbstractOptionList list)
                    for(var control:list.getControls())if(control.getOption()==integer("voxy_sectionrenderdistance")){
                        var event=new net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre(screen,control.getCenterX(),control.getCenterY(),1);
                        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
                        if(!event.isCanceled()||!(screen.getPrompt() instanceof SodiumPrompt))throw new AssertionError("Right-click precision input failed");found=true;break;
                    }
                if(!found)throw new AssertionError("Native control missing");
                phase++;ticks=0;}
            case 13->{if(ticks<10)return false;
                var screen=(net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen)mc.screen;screenshot("fantasy-town-voxy-precision.png");
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre(screen,256,0,0));
                var release=new net.neoforged.neoforge.client.event.ScreenEvent.KeyReleased.Pre(screen,256,0,0);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(release);
                if(screen.getPrompt()!=null||!release.isCanceled()||mc.screen!=screen)throw new AssertionError("Escape closed settings instead of only the input modal");
                System.out.println("HXZ_FEATURE PASS native Sodium import input, text events and precision-input modal");
                mc.setScreen(null);mc.player.connection.sendCommand("hxzfeature tall");phase++;ticks=0;}
            case 14->{if(ticks<35)return false;
                var option=(net.caffeinemc.mods.sodium.client.config.structure.BooleanOption)net.caffeinemc.mods.sodium.client.config.ConfigManager.CONFIG.getOption(SodiumPanel.id("hxz_qualityfirstlod"));
                if(!option.getAppliedValue())throw new AssertionError("Sodium quality-first default missing");
                option.modifyValue(false);net.caffeinemc.mods.sodium.client.config.ConfigManager.CONFIG.applyAllOptions();phase++;ticks=0;}
            case 15->{if(ticks<45)return false;
                if(HxzSettings.CONFIG.qualityFirstLod||!LiveClientConfig.message.equals("已应用并保存。")||VoxyCommon.getInstance()==null)
                    throw new AssertionError("Quality-first opt-out did not apply live: "+LiveClientConfig.message);
                var option=(net.caffeinemc.mods.sodium.client.config.structure.BooleanOption)net.caffeinemc.mods.sodium.client.config.ConfigManager.CONFIG.getOption(SodiumPanel.id("hxz_qualityfirstlod"));
                option.modifyValue(true);net.caffeinemc.mods.sodium.client.config.ConfigManager.CONFIG.applyAllOptions();phase++;ticks=0;}
            case 16->{if(ticks<45)return false;
                if(!HxzSettings.CONFIG.qualityFirstLod||!LiveClientConfig.message.equals("已应用并保存。")||VoxyCommon.getInstance()==null)
                    throw new AssertionError("Quality-first enable did not rebuild live: "+LiveClientConfig.message);
                QualityFirstProbe.verify();
                System.out.println("HXZ_QUALITY_FIRST PASS native Sodium Apply off/on with live renderer reconstruction");return true;}
        }
        return false;
    }
    private static net.caffeinemc.mods.sodium.client.config.structure.IntegerOption integer(String id){
        return (net.caffeinemc.mods.sodium.client.config.structure.IntegerOption)net.caffeinemc.mods.sodium.client.config.ConfigManager.CONFIG.getOption(SodiumPanel.id(id));
    }
    private static void screenshot(String name){var mc=Minecraft.getInstance();net.minecraft.client.Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->{});}
    private static Path fixture()throws Exception{
        RocksDB.loadLibrary();Path p=Path.of("import-test-source-"+UUID.randomUUID());Files.createDirectories(p);
        List<ColumnFamilyHandle> handles=new ArrayList<>();
        try(DBOptions options=new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);ColumnFamilyOptions family=new ColumnFamilyOptions()){
            var descriptors=List.of(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY,family),new ColumnFamilyDescriptor("world_sections".getBytes(),family),new ColumnFamilyDescriptor("id_mappings".getBytes(),family));
            try(var db=RocksDB.open(options,p.toString(),descriptors,handles)){
                long key=WorldEngine.getWorldSectionId(0,32,0,32);byte[] raw=new byte[16+32768*2+16];var bytes=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);bytes.putLong(key).putLong(2);bytes.putShort(16,(short)1);bytes.putLong(16+32768*2+8,Mapper.composeMappingId((byte)0xff,777,0));
                db.put(handles.get(1),ByteBuffer.allocate(8).putLong(key).array(),com.github.luben.zstd.Zstd.compress(raw));
                db.put(handles.get(2),ByteBuffer.allocate(4).putInt(0x40000000|777).array(),new Mapper.StateEntry(777,Blocks.DIAMOND_BLOCK.defaultBlockState()).serialize());
                db.put(handles.get(2),ByteBuffer.allocate(4).putInt(0x80000000).array(),new Mapper.BiomeEntry(0,"minecraft:plains").serialize());
                for(var h:handles)h.close();handles.clear();
            }
        }
        return p;
    }
    private static void tallClient()throws Exception{
        var mc=Minecraft.getInstance();var registry=mc.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var body=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());int[] ys=new int[65];body.writeVarInt(65);
        for(int i=0;i<65;i++){ys[i]=i-4;body.writeByte(ys[i]);new net.minecraft.world.level.chunk.LevelChunkSection(registry).write(body);body.writeBoolean(false).writeBoolean(false);}
        byte[] bytes=new byte[body.readableBytes()];body.readBytes(bytes);body.release();
        var payload=new dev.xantha.vss.networking.payloads.VoxelColumnS2CPayload(123,0,0,mc.level.dimension(),1L,bytes,true,4,0,1,ys);
        var assembledClass=Class.forName("dev.xantha.vss.networking.client.ClientColumnTransferAssembler$AssembledColumn");var ctor=assembledClass.getDeclaredConstructors()[0];ctor.setAccessible(true);
        Object assembled=ctor.newInstance(123,4L,0,0,mc.level.dimension(),1L,false,true,List.of(payload),bytes.length);
        var method=Class.forName("dev.xantha.vss.networking.client.ClientColumnProcessor").getDeclaredMethod("decodeColumn",assembledClass,net.minecraft.core.Registry.class);method.setAccessible(true);
        Object data=method.invoke(null,assembled,registry);Object[] sections=(Object[])data.getClass().getMethod("sections").invoke(data);if(sections.length!=65)throw new AssertionError("Client truncated tall column");
        var manifest=Class.forName("dev.xantha.vss.networking.client.ClientLodPresenceCache").getDeclaredMethod("encodeSectionManifest",int[].class);manifest.setAccessible(true);
        if(((byte[])manifest.invoke(null,(Object)ys)).length!=65)throw new AssertionError("Client presence cache truncated manifest");
        System.out.println("HXZ_FEATURE PASS actual client 65-section decoding and presence-cache manifest");
    }
}
