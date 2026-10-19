package dev.xantha.vss.networking.client;

import dev.xantha.vss.config.VSSClientConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.VoxyInstance;
import me.cortex.voxy.hxz.HxzSettings;
import me.cortex.voxy.hxz.core.LodIngestBudget;

/** Keeps compressed, bounded transfers queued until real terrain ingestion catches up. */
public final class ClientLodBackpressure {
    private static final LodIngestBudget BUDGET=new LodIngestBudget();
    private static VoxyInstance instance;
    private static ClientRequestTracker deadlineTracker;
    private static final Map<Integer,Long> QUEUED_DEADLINES=new HashMap<>();

    private ClientLodBackpressure() { }

    private static VoxyInstance currentInstance() {
        VoxyInstance current=VoxyCommon.getInstance();
        if(current!=instance) { BUDGET.reset();instance=current; }
        if(!HxzSettings.CONFIG.clientIngestBackpressure) { BUDGET.reset();return null; }
        return current;
    }

    private static int serviceWorkers() {
        int configured=Math.max(1,VoxyConfig.CONFIG.serviceThreads);
        var settings=HxzSettings.CONFIG;
        return settings.performanceScheduling&&settings.limitWorkers
                ? Math.max(1,Math.min(configured,settings.backgroundThreads)) : configured;
    }

    public static synchronized boolean pauseDrain() {
        var current=currentInstance();
        return current!=null&&BUDGET.pauseDrain(current.getIngestService().getTaskCount(),serviceWorkers());
    }

    public static synchronized boolean pauseRequests() {
        var current=currentInstance();
        if(current==null)return false;
        var processor=ClientColumnProcessor.activeProcessor;
        return BUDGET.pauseRequests(current.getIngestService().getTaskCount(),serviceWorkers(),
                processor==null?0:processor.getQueuedCount(),processor==null?0:processor.getQueuedBytes());
    }

    /** Received bytes wait on local ingestion, not on a server response anymore. */
    public static synchronized List<?> drainTimedOut(Object tracker,long nowNanos) {
        var requests=(ClientRequestTracker)tracker;
        if(deadlineTracker!=requests) { QUEUED_DEADLINES.clear();deadlineTracker=requests; }
        var processor=ClientColumnProcessor.activeProcessor;
        if(HxzSettings.CONFIG.clientIngestBackpressure && VSSClientConfig.CONFIG.receiveServerLods
                && processor!=null && processor.isActive()) {
            processor.refreshQueuedRequests(requests,nowNanos,QUEUED_DEADLINES);
        } else QUEUED_DEADLINES.clear();
        return requests.drainTimedOut(nowNanos);
    }

    public static synchronized void reset() {
        BUDGET.reset();instance=null;deadlineTracker=null;QUEUED_DEADLINES.clear();
    }
}
