package me.cortex.voxy.hxz.server;

/** Effective read-time caps protect old configurations without rewriting their values. */
public final class ServerResourceCaps {
    public static int cap(int value,String field){
        var p=ServerSafetyPolicy.limits;if(!p.enabled() || !p.limitResources())return value;
        int max=switch(field){
            case "diskReaderThreads","generationPackingThreads","automaticGenerationPackingThreads" -> p.workerThreads();
            case "diskReadQueueLimit","nbtReadQueueLimit","automaticGenerationPackingQueueLimit","persistentColumnCacheWriteQueueLimit" -> p.diskTasks();
            case "maxConcurrentNbtReads" -> p.workerThreads();
            case "sendPacketsPerTick" -> p.sendPackets();
            case "sendQueueLimitPerPlayer" -> p.queuedColumnsPerPlayer();
            case "sendQueueBytesLimitPerPlayer" -> p.sendQueueMiB()*1048576;
            case "columnCacheMaxBytes" -> p.cacheMiB()*1048576;
            case "columnCacheMaxEntries" -> 2048;
            case "dirtyVersionCacheMaxEntries" -> 25000;
            case "persistentColumnCacheMaxEntries" -> 100000;
            case "persistentColumnInvalidationBatchSize" -> 64;
            case "generationConcurrencyLimitPerPlayer","automaticGenerationStartsPerTick","automaticGenerationCompletionsPerTick" -> 1;
            case "generationConcurrencyLimitGlobal" -> 2;
            case "generationPackingQueueMaxMiB" -> p.packingMiB();
            case "generationCompletionBudgetMillis" -> 1;
            case "nearSyncRateLimitPerTick","midSyncRateLimitPerTick","farSyncRateLimitPerTick","distantSyncRateLimitPerTick" -> p.columnsPerPlayerTick();
            default -> Integer.MAX_VALUE;
        };
        if(max==Integer.MAX_VALUE)return value;
        // Zero means auto/unlimited for these source options.
        if(value==0 && (field.equals("generationPackingThreads") || field.endsWith("SyncRateLimitPerTick")))return max;
        return Math.min(Math.max(0,value),max);
    }
    public static boolean generation(boolean enabled){var p=ServerSafetyPolicy.limits;return enabled && (!p.enabled() || p.allowLodGeneration() && ServerTickGuard.accepting());}
    public static boolean unrestricted(boolean enabled){return enabled && !ServerSafetyPolicy.boundedResources();}
}
