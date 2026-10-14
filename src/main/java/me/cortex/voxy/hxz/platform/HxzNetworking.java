package me.cortex.voxy.hxz.platform;

import dev.xantha.vss.networking.payloads.ServerIdentityS2CPayload;
import dev.xantha.vss.networking.server.ServerIdentityConfigurationTask;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

public final class HxzNetworking {
    public static void registerConfigurationTasks(RegisterConfigurationTasksEvent event) {
        // Vanilla/unmodified clients must never receive a required custom task.
        if(NetworkRegistry.hasChannel((ICommonPacketListener)(Object)event.getListener(),ServerIdentityS2CPayload.TYPE.id()))
            event.register(new ServerIdentityConfigurationTask(event.getListener()));
    }
}
