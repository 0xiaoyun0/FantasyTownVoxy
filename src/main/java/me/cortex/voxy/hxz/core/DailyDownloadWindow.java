package me.cortex.voxy.hxz.core;

/** Server wall-clock window. End is exclusive; equal endpoints block all day. */
public record DailyDownloadWindow(boolean enabled,int startMinute,int endMinute,int utcOffsetMinutes) {
    public boolean blocked(long epochSecond) {
        if(!enabled)return false;
        int minute=(int)(Math.floorMod(epochSecond+utcOffsetMinutes*60L,86400L)/60L);
        return startMinute==endMinute || (startMinute<endMinute
                ? minute>=startMinute&&minute<endMinute : minute>=startMinute||minute<endMinute);
    }
}
