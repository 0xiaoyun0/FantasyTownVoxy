/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerPlayer
 */
package dev.xantha.vss.networking.server.runtime;

import dev.xantha.vss.common.VSSLogger;
import dev.xantha.vss.networking.server.dirty.DirtyColumnBroadcaster;
import dev.xantha.vss.networking.server.generation.ChunkGenerationService;
import dev.xantha.vss.networking.server.generation.ChunkyGenerationService;
import dev.xantha.vss.networking.server.preload.ExistingColumnPreloader;
import dev.xantha.vss.networking.server.runtime.DiskTaskRuntime;
import dev.xantha.vss.networking.server.runtime.ServerLifecycleGuard;
import dev.xantha.vss.networking.server.sending.GeneratedColumnFlusher;
import dev.xantha.vss.networking.server.sending.QueuedColumnSender;
import dev.xantha.vss.networking.server.state.PlayerRequestRegistry;
import dev.xantha.vss.networking.server.state.PlayerRequestState;
import dev.xantha.vss.networking.server.storage.ColumnLodCache;
import dev.xantha.vss.networking.server.storage.PersistentColumnLodStore;
import dev.xantha.vss.networking.server.storage.PersistentColumnWriter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerNetworkingLifecycle {
    private final PlayerRequestRegistry playerRegistry;
    private final ChunkGenerationService generationService;
    private final ColumnLodCache columnCache;
    private final PersistentColumnLodStore persistentStore;
    private final PersistentColumnWriter persistentColumnWriter;
    private final ServerLifecycleGuard lifecycleGuard;
    private final DiskTaskRuntime diskRuntime;
    private final GeneratedColumnFlusher generatedColumnFlusher;
    private final ExistingColumnPreloader existingColumnPreloader;
    private final QueuedColumnSender queuedColumnSender;
    private final ChunkyGenerationService chunky;
    private boolean idleMemoryReleased = true;
    private int nextStage;
    private boolean failureCleaned;

    public ServerNetworkingLifecycle(PlayerRequestRegistry playerRegistry, ChunkGenerationService generationService, ColumnLodCache columnCache, PersistentColumnLodStore persistentStore, PersistentColumnWriter persistentColumnWriter, ServerLifecycleGuard lifecycleGuard, DiskTaskRuntime diskRuntime, GeneratedColumnFlusher generatedColumnFlusher, ExistingColumnPreloader existingColumnPreloader, QueuedColumnSender queuedColumnSender, ChunkyGenerationService chunky) {
        this.playerRegistry = playerRegistry;
        this.generationService = generationService;
        this.columnCache = columnCache;
        this.persistentStore = persistentStore;
        this.persistentColumnWriter = persistentColumnWriter;
        this.lifecycleGuard = lifecycleGuard;
        this.diskRuntime = diskRuntime;
        this.generatedColumnFlusher = generatedColumnFlusher;
        this.existingColumnPreloader = existingColumnPreloader;
        this.queuedColumnSender = queuedColumnSender;
        this.chunky = chunky;
    }

    public void applyRuntimeConfig() {
        dev.xantha.vss.networking.WorldgenProfileTransfer.trim();
        this.generationService.applyRuntimeConfig();
        this.queuedColumnSender.applyRuntimeConfig();
        int resizedThreads = this.diskRuntime.resizeReadExecutor();
        if (resizedThreads > 0) {
            VSSLogger.info("VSS disk reader threads resized to " + resizedThreads);
        }
    }

    public void onPlayerLogout(ServerPlayer player) {
        me.cortex.voxy.hxz.server.ServerIngress.forget(player.connection.getConnection());
        PlayerRequestState state = this.playerRegistry.remove(player.getUUID());
        if (state != null) {
            state.clearAll();
        }
        this.generationService.removePlayer(player.getUUID());
        if (this.playerRegistry.isEmpty() && !this.chunky.hasActiveJob()) {
            this.releaseIdleMemory();
        }
    }

    public void markSessionActive() {
        this.idleMemoryReleased = false;
    }

    public void onServerTick(MinecraftServer server) {
        if (this.lifecycleGuard.isStopping()) {
            return;
        }
        var guard=me.cortex.voxy.hxz.server.ServerSafetyPolicy.limits;
        me.cortex.voxy.hxz.server.ServerDownloads.tick(server);
        me.cortex.voxy.hxz.server.ServerTickGuard.begin(server);
        if(me.cortex.voxy.hxz.server.ServerTickGuard.circuitOpen()){
            if(!failureCleaned){
                failureCleaned=true;
                me.cortex.voxy.hxz.server.ServerTickGuard.run("failure cleanup",()->{
                    this.chunky.clear();this.generationService.releaseIdleMemory();
                    for(var state:this.playerRegistry.states())state.clearAll();
                });
            }
            return;
        }
        failureCleaned=false;
        if (this.playerRegistry.isEmpty() && !this.chunky.hasActiveJob()) {
            me.cortex.voxy.hxz.server.ServerTickGuard.run("idle cleanup",this::releaseIdleMemory);
            return;
        }
        this.idleMemoryReleased = false;
        // Resume at the next stage on the next tick instead of starving the last services.
        for(int i=0;i<7 && me.cortex.voxy.hxz.server.ServerTickGuard.available();i++){
            int stage=nextStage;nextStage=(nextStage+1)%7;
            me.cortex.voxy.hxz.server.ServerTickGuard.run("LOD stage "+stage,()->{
                switch(stage){
                    case 0 -> this.generatedColumnFlusher.flush(server);
                    case 1 -> this.queuedColumnSender.flush(server);
                    case 2 -> {if(me.cortex.voxy.hxz.server.ServerTickGuard.accepting())this.chunky.tick(server);}
                    case 3 -> {if(me.cortex.voxy.hxz.server.ServerDownloads.allowed()&&me.cortex.voxy.hxz.server.ServerTickGuard.background())this.existingColumnPreloader.flushColumns(server);}
                    case 4 -> {if(me.cortex.voxy.hxz.server.ServerDownloads.allowed()&&me.cortex.voxy.hxz.server.ServerTickGuard.background())this.existingColumnPreloader.scanRegions(server);}
                    case 5 -> DirtyColumnBroadcaster.tick(server);
                    case 6 -> {this.persistentColumnWriter.flushInvalidations(server);this.persistentColumnWriter.scheduleMaintenance(server);}
                }
            });
        }
    }

    public void onServerStarting() {
        me.cortex.voxy.hxz.server.ServerDownloads.clear();
        dev.xantha.vss.networking.WorldgenProfileTransfer.clear();
        me.cortex.voxy.hxz.server.ServerTickGuard.start();
        nextStage=0;failureCleaned=false;
        this.chunky.clear();
        this.lifecycleGuard.start();
        this.queuedColumnSender.reset();
        this.diskRuntime.restart();
        this.applyRuntimeConfig();
        this.idleMemoryReleased = true;
        this.playerRegistry.clear();
        DirtyColumnBroadcaster.clear();
        this.columnCache.clear();
        this.persistentStore.clearMemory();
        this.diskRuntime.resetPendingCounts();
    }

    public void onServerStopping(MinecraftServer server) {
        me.cortex.voxy.hxz.server.ServerDownloads.clear();
        dev.xantha.vss.networking.WorldgenProfileTransfer.clear();
        me.cortex.voxy.hxz.server.ServerTickGuard.stop();
        this.chunky.clear();
        this.persistentColumnWriter.flushInvalidationsBlocking(server);
        this.persistentColumnWriter.flushWritesBlocking();
        this.lifecycleGuard.stop();
        if (!this.diskRuntime.awaitWrites(10000L)) {
            VSSLogger.warn("Timed out while flushing VSS persistent column writes during shutdown");
        }
        this.persistentStore.flushDirtyIndexesBlocking(server);
        this.diskRuntime.clearCoalescedReads();
        this.queuedColumnSender.reset();
        this.diskRuntime.shutdown();
        this.diskRuntime.resetPendingCounts();
        for (PlayerRequestState state : this.playerRegistry.states()) {
            state.clearAll();
        }
        this.idleMemoryReleased = false;
        this.generationService.shutdown();
        DirtyColumnBroadcaster.clear();
        this.columnCache.clear();
        this.persistentStore.clearMemory();
        this.idleMemoryReleased = true;
        this.playerRegistry.clear();
        VSSLogger.info("Stopped VSS LOD sync and cleared generation tickets during server shutdown");
    }

    private void releaseIdleMemory() {
        if (this.idleMemoryReleased) {
            return;
        }
        this.generationService.releaseIdleMemory();
        DirtyColumnBroadcaster.clear();
        this.idleMemoryReleased = true;
        VSSLogger.info("Released idle VSS request state after the last VSS player disconnected; kept hot LOD caches");
    }
}

