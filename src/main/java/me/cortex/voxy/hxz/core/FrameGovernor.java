package me.cortex.voxy.hxz.core;

/** Pure controller: independent switches for startup ramp and feedback. */
public final class FrameGovernor {
    private double distance=1,elapsed,stable,decision,cooldown,averageMs=1000.0/30;
    private long previous=Long.MIN_VALUE;
    private volatile boolean pressure;
    public void update(long now,double requested,int fps,double heap,int pending,double warmup,double growth,boolean adaptive,boolean progressive){
        requested=finiteClamp(requested,1,64,16);
        if(previous==Long.MIN_VALUE){previous=now;distance=progressive?Math.min(1,requested):requested;return;}
        double dt=(now-previous)*1e-9;previous=now;
        if(dt<=0||dt>1){stable=0;return;}
        elapsed+=dt;decision+=dt;cooldown=Math.max(0,cooldown-dt);
        averageMs+=(dt*1000-averageMs)*(1-Math.exp(-dt/.75));
        double targetMs=1000.0/Math.clamp(fps,30,240);
        pressure=averageMs>targetMs*1.04||heap>.82||pending>384;
        boolean headroom=averageMs<targetMs*.90&&heap<.72&&pending<128;
        // Frame intervals include VSync / FPS-limiter waits. At the target,
        // cautiously probe instead of demanding an impossible 10% higher FPS.
        boolean sustainable=averageMs<=targetMs*1.02&&heap<.72&&pending<128;
        stable=sustainable?stable+dt:0;
        distance=Math.min(requested,distance);
        if(!adaptive&&!progressive){distance=requested;return;}
        if(decision>=.5){double window=Math.min(1,decision);decision=0;
            if(adaptive&&pressure){distance=Math.max(1,distance*.85);cooldown=3;stable=0;}
            else if(cooldown==0&&(!progressive||elapsed>=warmup)&&(!adaptive||stable>=3))
                distance=Math.min(requested,distance+finiteClamp(growth,.02,2,.25)*window*(!adaptive||headroom?1:.5));
        }
    }
    public double distance(){return distance;}
    public double averageFps(){return 1000/averageMs;}
    public boolean underPressure(){return pressure;}
    public int meshRequestsPerSecond(){return pressure?8:elapsed<5?24:120;}
    public int columnsPerSecond(){return pressure?2:elapsed<5?4:12;}
    public int meshRequestsPerSecond(int maximum){return pressure?Math.min(8,maximum):elapsed<5?Math.min(24,maximum):maximum;}
    public int columnsPerSecond(int maximum){return pressure?Math.min(2,maximum):elapsed<5?Math.min(4,maximum):maximum;}
    public static double finiteClamp(double value,double min,double max,double fallback){return Double.isFinite(value)?Math.max(min,Math.min(max,value)):fallback;}
}
