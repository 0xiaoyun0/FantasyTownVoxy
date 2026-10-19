package me.cortex.voxy.client.config;
/** Avoid starting loader/platform configuration in the headless test. */
public final class VoxyConfig {
    public static final VoxyConfig CONFIG=new VoxyConfig();
    public int serviceThreads=2;
}
