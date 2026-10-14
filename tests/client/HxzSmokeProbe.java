package hxz.smoke;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import java.nio.file.*;

/** Test fixture only; never packaged in the release. */
@Mod("hxz_smoke_probe")
public final class HxzSmokeProbe {
    public static int farColumns;
    private int stable;
    private boolean captured;
    private boolean optionsTested;
    private int adminPhase,phaseTicks;
    private int floodPhase,floodTicks;
    public HxzSmokeProbe() { NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class,this::tick); }
    private void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        boolean world=Boolean.getBoolean("hxz.smoke.world");
        if(world&&mc.player!=null&&mc.player.isDeadOrDying()){
            mc.player.respawn();mc.setScreen(null);stable=0;return;
        }
        // A first-run changelog in this modpack blocks its title screen until acknowledged.
        if(mc.getOverlay()==null&&mc.screen!=null&&java.util.Set.of("com.maxenonyme.createsubmarine.submarine.client.DeepSeasUpdateScreen","dev.aika.smsn.client.gui.screen.VersionCheckWarningScreen").contains(mc.screen.getClass().getName())){
            if(mc.screen.getClass().getSimpleName().equals("VersionCheckWarningScreen")){
                for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().getString().equals(net.minecraft.network.chat.CommonComponents.GUI_PROCEED.getString())){button.onPress();System.out.println("HXZ_SMOKE acknowledged version notice");break;}
            }else{mc.screen.onClose();System.out.println("HXZ_SMOKE acknowledged changelog notice");}
            return;
        }
        boolean ready=world ? mc.level!=null && mc.player!=null : mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen
            ||captured&&mc.screen instanceof net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
        if(!ready || mc.getOverlay()!=null) { stable=0; return; }
        if(++stable<100) return;
        try {
            if(world&&Boolean.getBoolean("hxz.smoke.qualityVisual")){
                if(!QualityVisualProbe.tick())return;
                Files.writeString(Path.of(System.getProperty("hxz.smoke.dir"),"result.txt"),"PASS captured real vanilla/LOD handoff scene");mc.stop();return;
            }
            if(world&&System.getProperty("hxz.smoke.accuracy")!=null){
                if(!TerrainAccuracyProbe.tick())return;
                if(!captured){
                    mc.setScreen(me.cortex.voxy.hxz.client.SodiumPanel.createScreen(null));
                    ((net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen)mc.screen).jumpToPage(me.cortex.voxy.hxz.client.SodiumPanel.page("地形预测"));
                    captured=true;phaseTicks=0;return;
                }
                if(++phaseTicks<10)return;
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"fantasy-town-voxy-prediction.png",mc.getMainRenderTarget(),c->{});
                Files.writeString(Path.of(System.getProperty("hxz.smoke.dir"),"result.txt"),"PASS prediction accuracy "+System.getProperty("hxz.smoke.accuracy"));mc.stop();return;
            }
            if(world&&Boolean.getBoolean("hxz.smoke.proxy")){
                if(!XaeroTransferProbe.tick())return;
                Files.writeString(Path.of(System.getProperty("hxz.smoke.dir"),"result.txt"),"PASS real Velocity A/B/A map and LOD isolation");mc.stop();return;
            }
            if(world&&Boolean.getBoolean("hxz.smoke.xaero")&&!captured){XaeroStorageProbe.run();captured=true;}
            if(world&&Boolean.getBoolean("hxz.smoke.downloads")){
                if(!DownloadLiveProbe.tick())return;
                Files.writeString(Path.of(System.getProperty("hxz.smoke.dir"),"result.txt"),"PASS download policy");mc.stop();return;
            }
            if(world&&System.getProperty("hxz.smoke.persistence")!=null){
                if(!PersistenceProbe.tick())return;
                Files.writeString(Path.of(System.getProperty("hxz.smoke.dir"),"result.txt"),"PASS persistence "+System.getProperty("hxz.smoke.persistence"));
                mc.stop();return;
            }
            String details="";
            if(world&&Boolean.getBoolean("hxz.smoke.compatibility")&&!optionsTested){
                ModpackCompatibilityProbe.run();RtfPresetProbe.run();optionsTested=true;
                mc.setScreen(me.cortex.voxy.hxz.client.SodiumPanel.createScreen(null));
                ((net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen)mc.screen).jumpToPage(me.cortex.voxy.hxz.client.SodiumPanel.page("性能与加载"));
                return;
            }
            if(!world){
                if(!captured){
                    mc.setScreen(me.cortex.voxy.hxz.client.SodiumPanel.createScreen(mc.screen));
                    if(!(mc.screen instanceof net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen))throw new AssertionError("Full-pack native Sodium entry failed");
                    if(me.cortex.voxy.hxz.client.SodiumPanel.page("导入 LOD")==null||me.cortex.voxy.hxz.client.SodiumPanel.page("管理员 · 负载保护")==null)throw new AssertionError("Full-pack native pages missing");
                    captured=true;return;
                }
                if(stable<120)return;
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"fantasy-town-voxy-fullpack-sodium.png",mc.getMainRenderTarget(),c->{});
                details=", native Sodium panel opened";
            }
            if(world && System.getProperty("hxz.smoke.expectedSharing")!=null) {
                if(!optionsTested){
                    var settings=Class.forName("me.cortex.voxy.hxz.HxzSettings");
                    Object cfg=settings.getField("CONFIG").get(null);
                    boolean wasOriginal=settings.getField("originalRendering").getBoolean(cfg);
                    var client=Class.forName("me.cortex.voxy.hxz.client.HxzClient");
                    var commands=net.neoforged.neoforge.client.ClientCommandHandler.getDispatcher();
                    var source=net.neoforged.neoforge.client.ClientCommandHandler.getSource();
                    commands.execute("voxyhxz set limitCoarseMeshes false",source);
                    if(settings.getField("limitCoarseMeshes").getBoolean(cfg))throw new AssertionError("Command failed to disable coarse limit");
                    commands.execute("voxyhxz profile original",source);
                    if((Integer)client.getMethod("flags").invoke(null)!=0)throw new AssertionError("Original mode retained HXZ shader flags");
                    commands.execute("voxyhxz profile balanced",source);
                    if(!settings.getField("limitCoarseMeshes").getBoolean(cfg))throw new AssertionError("Balanced preset did not restore limit");
                    if(wasOriginal)commands.execute("voxyhxz profile original",source);
                    var vssCfg=Class.forName("dev.xantha.vss.config.VSSClientConfig");Object vss=vssCfg.getField("CONFIG").get(null);
                    boolean prediction=vssCfg.getField("enablePrediction").getBoolean(vss);
                    commands.execute("voxyhxz prediction false",source);
                    if(vssCfg.getField("enablePrediction").getBoolean(vss))throw new AssertionError("Prediction disable failed");
                    commands.execute("voxyhxz prediction "+prediction,source);
                    Class<?> manager=Class.forName("net.caffeinemc.mods.sodium.client.config.ConfigManager");
                    Object ui=manager.getField("CONFIG").get(null);
                    var get=ui.getClass().getMethod("getOption",net.minecraft.resources.ResourceLocation.class);
                    for(Object option:(java.util.List<?>)settings.getField("OPTIONS").get(null)){
                        String key=(String)option.getClass().getMethod("key").invoke(option);
                        if(get.invoke(ui,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("voxy","hxz_"+key.toLowerCase(java.util.Locale.ROOT)))==null)throw new AssertionError("GUI missing "+key);
                    }
                    Object instance=Class.forName("me.cortex.voxy.commonImpl.VoxyCommon").getMethod("getInstance").invoke(null);
                    if(instance==null)throw new AssertionError("No Voxy instance");
                    Path storage=(Path)instance.getClass().getMethod("getStorageBasePath").invoke(instance);
                    if(!storage.endsWith("hxz-neovoxy-0.6"))throw new AssertionError("Old cache path reused: "+storage);
                    RtfPresetProbe.run();
                    optionsTested=true;
                    System.out.println("HXZ_SMOKE PASS command toggles, original preset, every GUI option, isolated cache path");
                }
                if(Boolean.getBoolean("hxz.smoke.features")&&!HxzFeatureProbe.tick())return;
                boolean expected=Boolean.getBoolean("hxz.smoke.expectedSharing");
                var network=Class.forName("dev.xantha.vss.networking.client.VSSClientNetworking");
                boolean active=(Boolean)network.getMethod("isClientLodSessionActive").invoke(null);
                long columns=(Long)network.getMethod("getColumnsReceived").invoke(null);
                long bytes=(Long)network.getMethod("getBytesReceived").invoke(null);
                if(Boolean.getBoolean("hxz.smoke.flood") && active && columns>0){
                    if(floodPhase==0){mc.player.connection.sendCommand("hxzsafetytest arm");floodPhase=1;return;}
                    if(floodPhase==1){
                        long[] invalid={dev.xantha.vss.common.PositionUtil.packPosition(Integer.MIN_VALUE,0),dev.xantha.vss.common.PositionUtil.packPosition(0,Integer.MAX_VALUE)};
                        dev.xantha.vss.networking.VSSNetworking.sendToServer(new dev.xantha.vss.networking.payloads.BatchChunkRequestC2SPayload(new int[]{2_000_000,2_000_001},invalid,new long[2],new boolean[2],new boolean[2],2));
                        for(int batch=0;batch<256;batch++){
                            int[] ids=new int[64];long[] positions=new long[64];
                            for(int i=0;i<64;i++){ids[i]=1_000_000+batch*64+i;positions[i]=dev.xantha.vss.common.PositionUtil.packPosition(8+i%16,8+i/16);}
                            dev.xantha.vss.networking.VSSNetworking.sendToServer(new dev.xantha.vss.networking.payloads.BatchChunkRequestC2SPayload(ids,positions,new long[64],new boolean[64],new boolean[64],64));
                        }
                        floodPhase=2;System.out.println("HXZ_SMOKE sent 16384 LOD requests");return;
                    }
                    if(floodPhase==2){if(++floodTicks<500)return;mc.player.connection.sendCommand("hxzsafetytest finish");floodPhase=3;return;}
                    if(floodPhase==3 && ++floodTicks<550)return;
                }
                if(Boolean.getBoolean("hxz.smoke.adminToggle")){
                    if(adminPhase==0 && active && farColumns>0){mc.player.connection.sendCommand("voxyhxzserver sharing off");adminPhase=1;phaseTicks=0;return;}
                    if(adminPhase==1){
                        if(++phaseTicks>200)throw new AssertionError("Admin disable did not deactivate session");
                        if(!active){System.out.println("HXZ_SMOKE PASS admin sharing off on live connection");mc.player.connection.sendCommand("voxyhxzserver sharing on");adminPhase=2;phaseTicks=0;}
                        return;
                    }
                    if(adminPhase==2){
                        if(++phaseTicks>200)throw new AssertionError("Admin enable did not reactivate session");
                        if(active){System.out.println("HXZ_SMOKE PASS admin sharing on on live connection");adminPhase=3;}else return;
                    }
                }
                if(stable%100==0) System.out.println("HXZ_SMOKE active="+active+", columns="+columns+", farColumns="+farColumns);
                if(expected && (!active || columns==0 || farColumns==0) && stable<2400) return;
                if(active!=expected || (expected && (columns==0 || farColumns==0)) || (!expected && columns!=0))
                    throw new AssertionError("Sharing state: expected="+expected+", active="+active+", columns="+columns+", farColumns="+farColumns);
                details=", sharing="+active+", columns="+columns+", farColumns="+farColumns+", bytes="+bytes;
            }
            if(world && !captured) {
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,mc.getMainRenderTarget(),text->{});
                captured=true; return;
            }
            if(world && stable<120) return;
            if(Boolean.getBoolean("hxz.smoke.compatibility"))net.minecraft.client.Screenshot.grab(mc.gameDirectory,"fantasy-town-voxy-performance.png",mc.getMainRenderTarget(),c->{});
            String result="PASS "+(world?"client joined world":"client reached title screen")+" after stable loading"+details;
            Files.writeString(Path.of(System.getProperty("hxz.smoke.dir"),"result.txt"),result);
            System.out.println("HXZ_SMOKE "+result);
        } catch(Exception | AssertionError e) {
            e.printStackTrace();
            try { Files.writeString(Path.of(System.getProperty("hxz.smoke.dir"),"result.txt"),"FAIL "+e); }
            catch(java.io.IOException writeFailure) { throw new RuntimeException(writeFailure); }
        }
        mc.stop();
    }
}
