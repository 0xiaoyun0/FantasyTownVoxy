package me.cortex.voxy.hxz.platform;
import dev.xantha.vss.networking.VSSNetworking;
import dev.xantha.vss.networking.server.VSSServerNetworking;
import dev.xantha.vss.networking.server.broadcast.FarPlayerBroadcaster;
import dev.xantha.vss.networking.server.command.VSSServerCommands;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
public final class HxzBootstrap {
    public static void init(IEventBus bus,ModContainer container){
        HxzDefaults.install();
        me.cortex.voxy.hxz.server.ServerSafetyConfig.init();
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre.class,me.cortex.voxy.hxz.server.ServerTickGuard::preTick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,false,net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class,me.cortex.voxy.hxz.server.ServerTickGuard::postTick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,false,net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent.class,me.cortex.voxy.hxz.server.ServerIngress::changedDimension);
        bus.addListener(RegisterPayloadHandlersEvent.class,VSSNetworking::register);
        bus.addListener(RegisterPayloadHandlersEvent.class,AdminNetwork::register);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class,AdminNetwork::tick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStoppedEvent.class,e->AdminNetwork.clear());
        bus.addListener(RegisterConfigurationTasksEvent.class,VSSNetworking::registerConfigurationTasks);
        NeoForge.EVENT_BUS.register(VSSServerNetworking.class);
        NeoForge.EVENT_BUS.register(FarPlayerBroadcaster.class);
        NeoForge.EVENT_BUS.register(VSSServerCommands.class);
        NeoForge.EVENT_BUS.addListener(HxzServerCommands::register);
        if(!HxzPlatform.dedicatedServer()){
            try{Class.forName("me.cortex.voxy.hxz.client.HxzClientBootstrap").getMethod("init",IEventBus.class,ModContainer.class).invoke(null,bus,container);}
            catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }
    }
}
