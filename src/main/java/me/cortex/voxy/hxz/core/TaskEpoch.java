package me.cortex.voxy.hxz.core;

/** Tokens travel with asynchronous work; clearing a queue alone cannot invalidate a producer. */
public final class TaskEpoch {
    private volatile long value;
    public long capture(){return value;}
    public synchronized void advance(){value++;}
    public boolean current(long token){return value==token;}
}
