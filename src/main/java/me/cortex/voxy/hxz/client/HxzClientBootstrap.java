package me.cortex.voxy.hxz.client;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
public final class HxzClientBootstrap {
    public static void init(IEventBus bus,ModContainer container){
        dev.xantha.vss.client.VSSClientBootstrap.init(bus,container);
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,(mod,parent)->SodiumPanel.createScreen(parent));
        me.cortex.voxy.hxz.platform.AdminNetwork.client=AdminClient::receive;
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,e->{LiveClientConfig.tick();SodiumPanel.tick();});
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre.class,SodiumPanel::precisionInput);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ScreenEvent.CharacterTyped.Pre.class,e->{
            if(e.getScreen() instanceof net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen screen&&screen.getPrompt() instanceof SodiumPrompt prompt){prompt.charTyped(e.getCodePoint(),e.getModifiers());e.setCanceled(true);}
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre.class,SodiumPanel::modalKeyPressed);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ScreenEvent.KeyReleased.Pre.class,SodiumPanel::modalKeyReleased);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut.class,e->{AdminClient.clear();me.cortex.voxy.hxz.core.SessionNamespaces.endSession();});
        HxzClient.initialize();
        bus.addListener(FMLClientSetupEvent.class,e->e.enqueueWork(()->{
            me.cortex.voxy.client.VoxyClient.initVoxyClient();
            me.cortex.voxy.client.VoxyClient.onClientSetup();
        }));
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class,e->{
            HxzClientCommands.register(e.getDispatcher());
            if(me.cortex.voxy.commonImpl.VoxyCommon.isAvailable())e.getDispatcher().register(me.cortex.voxy.client.VoxyCommands.register());
        });
    }
}
