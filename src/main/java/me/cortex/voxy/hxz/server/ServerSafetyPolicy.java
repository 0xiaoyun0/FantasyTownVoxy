package me.cortex.voxy.hxz.server;

/** Immutable, safely published limits; also usable before Minecraft has started. */
public final class ServerSafetyPolicy {
    public static volatile boolean filterSablePlots=true;
    public record ProfileTransfers(boolean enabled,int mebibytes){}
    public static volatile ProfileTransfers profileTransfers=new ProfileTransfers(true,16);
    public record Limits(boolean enabled, boolean limitRequests, boolean tickBudget,
        boolean pauseOnOverload, boolean limitResources, boolean isolateErrors,
        boolean allowLodGeneration, boolean backgroundPreload, int columnsPerTick,
        int columnsPerPlayerTick, int queuedColumns, int queuedColumnsPerPlayer,
        int tickBudgetMillis, int pauseAboveMillis, int heapPercent, int cooldownSeconds,
        int diskTasks, int workerThreads, int completionTasks, int sendPackets,
        int sendQueueMiB, int cacheMiB, int packingMiB) {}
    public static volatile Limits limits = new Limits(true,true,true,true,true,true,false,false,
        16,4,512,64,4,80,85,5,64,2,32,8,8,32,8);
    private ServerSafetyPolicy() {}
    public static int diskLimit(int requested) {
        var p=limits;
        return p.enabled() && p.limitResources() ? Math.min(Math.max(0,requested),p.diskTasks()) : requested;
    }
    public static boolean boundedResources() { var p=limits;return p.enabled() && p.limitResources(); }
}
