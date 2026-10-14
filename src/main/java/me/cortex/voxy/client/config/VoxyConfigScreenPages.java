package me.cortex.voxy.client.config;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
public final class VoxyConfigScreenPages implements ConfigEntryPoint {
    @Override public void registerConfigLate(ConfigBuilder builder){me.cortex.voxy.hxz.client.HxzOptions.add(builder,builder.registerOwnModOptions().setName("Fantasy Town Voxy"));}
}
