package hxz.smoke;
import me.cortex.voxy.hxz.client.*;
import net.minecraft.client.Minecraft;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.*;
import dev.xantha.vss.networking.client.VSSClientNetworking;
import java.util.Map;

public final class DownloadLiveProbe {
    private static int phase,ticks,count;
    public static boolean tick()throws Exception {
        var mc=Minecraft.getInstance();boolean active=VSSClientNetworking.isClientLodSessionActive();
        if(++ticks>1800)throw new AssertionError("Download policy timed out at phase "+phase+": "+AdminClient.message);
        switch(phase){
            case 0->{if(active||HxzSmokeProbe.farColumns!=0)throw new AssertionError("Downloaded inside blocked window on initial join");
                mc.player.connection.sendCommand("voxyhxzserver downloads timezone +08:00");
                mc.player.connection.sendCommand("voxyhxzserver downloads rate 256 64");mc.player.connection.sendCommand("voxyhxzserver downloads off");next();}
            case 1->{if(!active||HxzSmokeProbe.farColumns==0)return false;
                System.out.println("HXZ_DOWNLOAD PASS initial blocked join downloaded nothing; online schedule disable started sharing");
                mc.player.connection.sendCommand("voxyhxzserver downloads block 00:00 00:00");next();}
            case 2->{if(active||ticks<40)return false;count=HxzSmokeProbe.farColumns;next();}
            case 3->{if(ticks<100)return false;if(active||count!=HxzSmokeProbe.farColumns)throw new AssertionError("Server sent LOD while time window was blocked");
                var section=me.cortex.voxy.commonImpl.WorldIdentifier.ofEngine(mc.level).acquireIfExists(0,0,-2,4);
                if(section==null)throw new AssertionError("Blocking downloads removed existing local LOD");section.release();
                System.out.println("HXZ_DOWNLOAD PASS live schedule blocks further LOD while cached terrain remains available");
                AdminClient.request("get",Map.of());next();}
            case 4->{if(!AdminClient.allowed||AdminClient.pending||AdminClient.updated)return false;
                if(!AdminClient.value("safety.downloadScheduleEnabled",false).equals(true)||!AdminClient.value("safety.downloadPlayerBytesPerSecond",0).equals(65536)||!AdminClient.value("vss.totalBandwidthBytesPerSecond",0).equals(262144))throw new AssertionError("Command limits not persisted/reported");
                mc.setScreen(SodiumPanel.createScreen(null));((net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen)mc.screen).jumpToPage(SodiumPanel.page("管理员 · 下载限制"));
                ((IntegerOption)ConfigManager.CONFIG.getOption(SodiumPanel.adminId("safety.downloadPlayerBytesPerSecond"))).modifyValue(32768);
                ((BooleanOption)ConfigManager.CONFIG.getOption(SodiumPanel.adminId("safety.downloadScheduleEnabled"))).modifyValue(false);
                ConfigManager.CONFIG.applyAllOptions();next();}
            case 5->{if(AdminClient.pending||!active||ticks<25)return false;
                if(!AdminClient.value("safety.downloadPlayerBytesPerSecond",0).equals(32768)||!AdminClient.value("safety.downloadScheduleEnabled",true).equals(false))throw new AssertionError("Native Sodium Apply did not update download policy");
                net.minecraft.client.Screenshot.grab(mc.gameDirectory,"fantasy-town-voxy-downloads.png",mc.getMainRenderTarget(),text->{});
                System.out.println("HXZ_DOWNLOAD PASS OP time/timezone/rate commands, immediate read -> native Sodium Apply and online resume with new personal byte cap");return true;}
        }
        return false;
    }
    private static void next(){phase++;ticks=0;}
}
