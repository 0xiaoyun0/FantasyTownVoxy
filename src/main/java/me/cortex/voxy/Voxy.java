package me.cortex.voxy;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.hxz.platform.HxzBootstrap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
@Mod("voxy")
public class Voxy {
    public static final String MODID="voxy";
    public Voxy(IEventBus bus,ModContainer container){
        VoxyCommon.cleanInit(container.getModInfo().getVersion().toString(),me.cortex.voxy.hxz.platform.HxzPlatform.dedicatedServer());
        bus.addListener(me.cortex.voxy.common.network.VoxyNetworkHandler::register);
        HxzBootstrap.init(bus,container);
    }
}
