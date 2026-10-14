package hxz.smoke;

import java.nio.file.*;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/** Actual RTF water/land and mountains: compare full vanilla coverage with LOD handoff. */
public final class QualityVisualProbe {
    private static int phase,ticks,scene=Integer.getInteger("hxz.smoke.qualityScene",0);
    private static boolean contractChecked;
    private static volatile boolean positioned;
    private static volatile Throwable failure;
    private static final int[][] POSITIONS={{8,8},{3080,-4088}};
    private static void capture(String suffix)throws Exception{
        var mc=Minecraft.getInstance();String name="quality-"+scene+"-"+suffix;
        net.minecraft.client.Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),c->{});
        var target=mc.getMainRenderTarget();
        String status=name+", vanilla="+mc.options.renderDistance().get()+", position="+mc.player.position()+", width="+target.width+", height="+target.height+"\n";
        if(Boolean.getBoolean("hxz.smoke.qualityPrediction")){
            String quality=QualityFirstProbe.inspectRuntime();
            status+=quality+"\n";System.out.println(quality);
            StringBuilder bridge=new StringBuilder("HXZ_NORMAL_BRIDGE ");
            for(String key:new String[]{"failed","reported","completedViewport","completedFramebuffer"}){
                var f=dev.xantha.vss.client.prediction.PredictionNormalTranslucencyBridge.class.getDeclaredField(key);
                f.setAccessible(true);bridge.append(key).append('=').append(f.get(null)).append(' ');
            }
            System.out.println(bridge+" enabled="+me.cortex.voxy.hxz.HxzSettings.CONFIG.predictionDepthCompatibility);
            if(bridge.toString().contains("failed=true"))throw new AssertionError("Prediction/water integration failed: "+bridge);
            if(suffix.equals("handoff")&&!quality.contains("accepted=0 ")
                    &&(!bridge.toString().contains("failed=false reported=true")||bridge.toString().contains("completedViewport=null")))
                throw new AssertionError("Detailed prediction/water integration did not run: "+bridge);
            int reported=0;
            for(var tile:dev.xantha.vss.client.prediction.ClientPredictionState.readyTiles(mc.level.dimension())){
                if(tile.spacingBlocks()<16||reported++>=12)continue;
                int floating=0,lower=0,volume=0,missing=0;
                for(var s:tile.samples()){if(s.floating())floating++;if(s.hasLowerSpan())lower++;if(s.volume()!=null)volume++;if(!s.hasSurface())missing++;}
                System.out.println("HXZ_READY_SURFACE "+tile.key()+", step="+tile.spacingBlocks()+", vertices="+tile.mesh().vertexCount()+", floating="+floating+", lower="+lower+", volume="+volume+", missing="+missing);
            }
            status+=dev.xantha.vss.client.prediction.ClientPredictionState.diagnostics()+"\n";
            status+=dev.xantha.vss.client.prediction.PredictionRenderer.diagnostics()+"\n";
            if(suffix.equals("handoff")&&!dev.xantha.vss.client.prediction.ClientPredictionState.hasReadyTiles(mc.level.dimension()))
                throw new AssertionError("Prediction scene has no actual ready prediction tiles");
        }
        Files.writeString(Path.of(System.getProperty("hxz.smoke.dir"),"quality-visual.txt"),status,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        System.out.println("QUALITY_VISUAL captured "+status);
    }
    public static boolean tick()throws Exception{
        var mc=Minecraft.getInstance();ticks++;
        if(failure!=null)throw new AssertionError("RTF scene setup failed",failure);
        if(!contractChecked&&Boolean.getBoolean("hxz.smoke.smoothContract")){
            SmoothPreviewProbe.verify();QualityFirstProbe.verify();contractChecked=true;
        }
        if(phase==0){
            mc.options.renderDistance().set(8);mc.options.fov().set(70);mc.options.hideGui=true;
            mc.options.bobView().set(false);mc.options.entityShadows().set(false);mc.options.broadcastOptions();
            positioned=false;
            mc.getSingleplayerServer().execute(()->{try{
                var server=mc.getSingleplayerServer();var level=server.overworld();
                if(!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator))
                    throw new AssertionError("Quality test requires an actual RTF noise world");
                level.setDayTime(6000);level.setWeatherParameters(100000,0,false,false);
                level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,server);
                var player=server.getPlayerList().getPlayers().getFirst();player.setGameMode(GameType.SPECTATOR);
                int x=POSITIONS[scene][0],z=POSITIONS[scene][1];level.getChunk(x>>4,z>>4);
                int height=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);
                player.teleportTo(level,x,height+12,z,Set.of(),0,25);positioned=true;
            }catch(Throwable error){failure=error;}});phase=1;ticks=0;
            System.out.println("QUALITY_VISUAL collect actual RTF scene "+scene+" at vanilla radius 8");return false;
        }
        if(!positioned){ticks=0;return false;}
        mc.player.setYRot(0);mc.player.setXRot(25);mc.player.setDeltaMovement(0,0,0);
        if(phase==1&&ticks>=800){
            capture("vanilla");
            mc.options.renderDistance().set(2);mc.options.broadcastOptions();mc.levelRenderer.allChanged();
            phase=2;ticks=0;System.out.println("QUALITY_VISUAL vanilla radius now 2; keep cached LOD");return false;
        }
        if(phase==2&&ticks>=400){
            if(Boolean.getBoolean("hxz.smoke.qualityPrediction")){
                if(ticks%400==0){
                    System.out.println(QualityFirstProbe.inspectRuntime());
                    System.out.println("HXZ_QUALITY_WAIT "+dev.xantha.vss.client.prediction.ClientPredictionState.diagnostics());
                }
                // Empty prediction is intentional while only coarse terrain exists. Require actual
                // generated terrain so this tests rejection, not a disabled or broken predictor.
                if(!dev.xantha.vss.client.prediction.ClientPredictionState.hasReadyTiles(mc.level.dimension())){
                    if(ticks>=3600)throw new AssertionError("No actual RTF prediction completed; "+QualityFirstProbe.inspectRuntime());
                    return false;
                }
                String quality=QualityFirstProbe.inspectRuntime();
                if(quality.contains("accepted=0 ")){
                    var rendered=java.util.regex.Pattern.compile("(?:^|,)rendered=(\\d+)").matcher(dev.xantha.vss.client.prediction.PredictionRenderer.diagnostics());
                    if(!rendered.find()||Long.parseLong(rendered.group(1))!=0)
                        throw new AssertionError("Hidden coarse terrain was still drawn: "+quality);
                    System.out.println("HXZ_QUALITY_RUNTIME PASS coarse terrain generated but not drawn; waiting for detail");
                }
            }
            capture("handoff");
            if(++scene==POSITIONS.length)return true;
            phase=0;ticks=0;
        }
        return false;
    }
}
