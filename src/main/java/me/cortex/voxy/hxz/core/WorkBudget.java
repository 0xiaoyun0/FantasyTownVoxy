package me.cortex.voxy.hxz.core;

/** Bounded token bucket; the same wall-clock interval yields the same work at any FPS. */
public final class WorkBudget {
    public record Requests(int total,int background) {}
    /** Give background tokens their own small allowance within the GPU queue cap. */
    public static Requests allocate(int capacity,int foreground,int background,boolean limitForeground){
        capacity=Math.max(0,capacity);
        foreground=limitForeground?Math.clamp(foreground,0,capacity):capacity;
        background=Math.clamp(background,0,limitForeground?capacity-foreground:Math.max(0,capacity-1));
        return new Requests(limitForeground?foreground+background:capacity,background);
    }
    private long previous = Long.MIN_VALUE;
    private double tokens;
    public int take(long now, double perSecond, int maxBurst) {
        if (previous == Long.MIN_VALUE) { previous = now; return 0; }
        double dt = Math.max(0, Math.min(0.1, (now-previous)*1e-9));
        previous = now;
        tokens = Math.min(Math.max(0,maxBurst),tokens + dt*Math.max(0,perSecond));
        int count = (int)tokens;
        tokens -= count;
        return count;
    }
}
