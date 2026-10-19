package me.cortex.voxy.hxz;
/** Headless configuration fixture only; not included in the mod. */
public final class HxzSettings {
    public static final HxzSettings CONFIG=new HxzSettings();
    public boolean clientIngestBackpressure=true,performanceScheduling=true,limitWorkers=true;
    public int backgroundThreads=2;
}
