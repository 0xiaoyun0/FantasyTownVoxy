package me.cortex.voxy.hxz.server;

import java.util.*;
import me.cortex.voxy.hxz.core.*;
import dev.xantha.vss.config.VSSServerConfig;
import dev.xantha.vss.networking.VSSNetworking;
import dev.xantha.vss.networking.payloads.HandshakeRequestS2CPayload;
import dev.xantha.vss.networking.server.VSSServerNetworking;
import net.minecraft.server.MinecraftServer;

/** Server-authoritative admission and pacing, independent of the CPU safety switch. */
public final class ServerDownloads {
    public record Policy(DailyDownloadWindow window,boolean personalLimit,int personalBytes){}
    private static volatile Policy policy=new Policy(new DailyDownloadWindow(false,1020,1380,480),true,131072);
    private static final DownloadBandwidth<UUID> bandwidth=new DownloadBandwidth<>();
    private static boolean lastBlocked;
    public static void configure(ServerSafetyConfig c){
        policy=new Policy(new DailyDownloadWindow(c.downloadScheduleEnabled,c.downloadBlockStartHour*60+c.downloadBlockStartMinute,
            c.downloadBlockEndHour*60+c.downloadBlockEndMinute,c.downloadUtcOffsetMinutes),c.downloadLimitPerPlayer,c.downloadPlayerBytesPerSecond);
    }
    public static boolean allowed(){return !policy.window().blocked(System.currentTimeMillis()/1000L);}
    public static boolean sharing(boolean enabled){return enabled&&allowed();}
    public static long personalRate(long requested){var p=policy;return p.personalLimit()?Math.min(requested,p.personalBytes()):requested;}
    public static boolean take(UUID player,int bytes){
        var p=policy;
        return allowed()&&bandwidth.take(player,bytes,VSSServerConfig.CONFIG.totalBandwidthBytesPerSecond(),
                p.personalLimit()?p.personalBytes():0,System.nanoTime());
    }
    public static void tick(MinecraftServer server){
        boolean blocked=!allowed();if(blocked==lastBlocked)return;lastBlocked=blocked;
        VSSServerNetworking.bumpAndRefreshSessionConfigs(server);
        // A player who joined during the blocked window may never have had a
        // registered sharing session. Ask all compatible online clients again.
        if(!blocked)for(var player:server.getPlayerList().getPlayers())VSSNetworking.sendToPlayer(player,new HandshakeRequestS2CPayload());
        dev.xantha.vss.common.VSSLogger.info("Fantasy Town Voxy downloads "+(blocked?"paused by daily schedule":"resumed after daily schedule"));
    }
    public static void clear(){bandwidth.clear();lastBlocked=false;}
    public static String status(){var p=policy;var w=p.window();
        return String.format(Locale.ROOT,"下载：%s；禁用时段 %02d:%02d—%02d:%02d（UTC%+d 分钟，%s）；全服 %d B/s；每玩家 %s",
            allowed()?"允许":"时段内暂停",w.startMinute()/60,w.startMinute()%60,w.endMinute()/60,w.endMinute()%60,w.utcOffsetMinutes(),
            w.enabled()?"启用":"未启用",VSSServerConfig.CONFIG.totalBandwidthBytesPerSecond(),p.personalLimit()?p.personalBytes()+" B/s":"未另设上限");
    }
}
