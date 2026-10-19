package me.cortex.voxy.hxz.core;

/** Hysteresis for the existing VSS decode queue and Voxy's downstream section queue. */
public final class LodIngestBudget {
    public static final int MAX_DECODE_COLUMNS=1024;
    public static final long MAX_DECODE_BYTES=32L*1024L*1024L;
    private boolean drainPaused;
    private boolean requestsPaused;

    public static int sectionHighWater(int serviceWorkers) {
        return Math.max(256,Math.min(1024,Math.max(1,Math.min(8,serviceWorkers))*128));
    }

    public synchronized boolean pauseDrain(int pendingSections,int serviceWorkers) {
        int high=sectionHighWater(serviceWorkers);
        int pending=Math.max(0,pendingSections);
        drainPaused=drainPaused ? pending>high/2 : pending>=high;
        return drainPaused;
    }

    public synchronized boolean pauseRequests(int pendingSections,int serviceWorkers,int decodeColumns,long decodeBytes) {
        // Stop producing before the consumer's hard stop; already requested columns can still arrive.
        int high=sectionHighWater(serviceWorkers)/2;
        int sections=Math.max(0,pendingSections), columns=Math.max(0,decodeColumns);
        long bytes=Math.max(0,decodeBytes);
        requestsPaused=requestsPaused
                ? sections>high/2 || columns>MAX_DECODE_COLUMNS/2 || bytes>MAX_DECODE_BYTES/2
                : sections>=high || columns>=MAX_DECODE_COLUMNS*3/4 || bytes>=MAX_DECODE_BYTES*3/4;
        return requestsPaused;
    }

    public synchronized void reset() { drainPaused=false;requestsPaused=false; }
}
