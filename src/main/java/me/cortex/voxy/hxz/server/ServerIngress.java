package me.cortex.voxy.hxz.server;

import dev.xantha.vss.config.VSSServerConfig;
import dev.xantha.vss.networking.payloads.*;
import dev.xantha.vss.networking.server.VSSServerNetworking;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Network-thread admission only; all world/session operations remain on the server thread. */
public final class ServerIngress {
    private record Column(IPayloadContext context,int id,long position,long timestamp,boolean generation,boolean probe) {}
    private record Control(IPayloadContext context,CustomPacketPayload payload,long created) {}
    private static final FairWorkQueue<Connection,Column> columns=new FairWorkQueue<>();
    private static final FairWorkQueue<Connection,Control> controls=new FairWorkQueue<>();
    private static final FairWorkQueue<Connection,Control> presence=new FairWorkQueue<>();
    private static final Map<Connection,Peer> peers=new IdentityHashMap<>();
    private static final LongAdder accepted=new LongAdder(),rejected=new LongAdder();
    private static final class Peer{long window,replyAt,handshakeAt;int columns,controls,cancels;}

    public static void receive(CustomPacketPayload payload,IPayloadContext context){
        // Enforce before either protected or legacy main-thread dispatch. A
        // modified client cannot download during the server's blocked window.
        if(!ServerDownloads.allowed()&&(payload instanceof BatchChunkRequestC2SPayload||payload instanceof RegionPresenceC2SPayload)){
            rejected.increment();return;
        }
        var p=ServerSafetyPolicy.limits;
        if(!p.enabled() || !p.limitRequests()){
            context.enqueueWork(()->ServerTickGuard.run("legacy packet",()->dispatch(payload,context)));return;
        }
        Connection connection=context.connection();if(!connection.isConnected())return;
        long now=System.nanoTime();
        synchronized(peers){
            Peer peer=peers.get(connection);
            if(peer==null){if(peers.size()>=512){rejected.increment();return;}peer=new Peer();peers.put(connection,peer);}
            if(now-peer.window>=1_000_000_000L){peer.window=now;peer.columns=0;peer.controls=0;peer.cancels=0;}
            if(payload instanceof BatchChunkRequestC2SPayload batch){
                int[] refused=new int[batch.count()];int refusedCount=0;
                boolean admit=ServerTickGuard.accepting() && VSSServerConfig.CONFIG.enabled;
                for(int i=0;i<batch.count();i++){
                    int id=batch.requestIds()[i];long position=batch.packedPositions()[i];
                    // Keep the original request alive; backpressuring a duplicate ID would cancel it client-side.
                    if(columns.contains(connection,id)){rejected.increment();continue;}
                    int x=dev.xantha.vss.common.PositionUtil.unpackX(position),z=dev.xantha.vss.common.PositionUtil.unpackZ(position);
                    boolean valid=id>=0 && Math.abs((long)x)<=1_875_000 && Math.abs((long)z)<=1_875_000;
                    if(valid && admit && peer.columns<p.columnsPerPlayerTick()*20
                        && columns.offer(connection,id,new Column(context,id,position,batch.clientTimestamps()[i],batch.allowGeneration()[i],batch.cacheProbe()[i]),now,p.queuedColumns(),p.queuedColumnsPerPlayer())){
                        peer.columns++;accepted.increment();
                    }else{refused[refusedCount++]=id;rejected.increment();}
                }
                if(refusedCount>0 && now>=peer.replyAt){
                    peer.replyAt=now+1_000_000_000L;byte[] codes=new byte[refusedCount];Arrays.fill(codes,(byte)3);
                    reply(context,new BatchResponseS2CPayload(codes,Arrays.copyOf(refused,refusedCount),refusedCount));
                }
                return;
            }
            long key;
            if(payload instanceof CancelRequestC2SPayload cancel){
                if(cancel.requestId()<0 || ++peer.cancels>64)return;
                columns.remove(connection,cancel.requestId());key=-100L-cancel.requestId();
            }else{
                if(++peer.controls>4)return;
                if(payload instanceof HandshakeC2SPayload){if(now<peer.handshakeAt)return;peer.handshakeAt=now+5_000_000_000L;}
                if(payload instanceof RegionPresenceC2SPayload && !p.backgroundPreload())return;
                key=payload instanceof HandshakeC2SPayload?-1:payload instanceof BandwidthUpdateC2SPayload?-2:
                    payload instanceof RegionPresenceC2SPayload?-3:-4;
            }
            if(payload instanceof RegionPresenceC2SPayload){
                // Large compressed summaries have their own capacity; they cannot block handshakes.
                if(!presence.offer(connection,key,new Control(context,payload,now),now,2,1))rejected.increment();
            }else if(!controls.offer(connection,key,new Control(context,payload,now),now,128,8))rejected.increment();
        }
    }
    public static void drain(MinecraftServer server,long deadline){
        var p=ServerSafetyPolicy.limits;var used=new IdentityHashMap<Connection,Integer>();
        for(int i=0;i<2 && ServerTickGuard.available() && System.nanoTime()<deadline;i++){
            var item=controls.poll(used,1);if(item==null)break;
            if(valid(item.peer(),item.value().context()) && System.nanoTime()-item.enqueuedNanos()<10_000_000_000L)
                ServerTickGuard.run("control packet",()->dispatch(item.value().payload(),item.value().context()));
        }
        used.clear();
        if(server.getTickCount()%10==0 && ServerTickGuard.background() && ServerTickGuard.available()){
            var item=presence.poll(used,1);
            if(item!=null && valid(item.peer(),item.value().context()) && System.nanoTime()-item.enqueuedNanos()<10_000_000_000L)
                ServerTickGuard.run("presence summary",()->dispatch(item.value().payload(),item.value().context()));
            used.clear();
        }
        for(int i=0;i<p.columnsPerTick() && ServerTickGuard.available() && System.nanoTime()<deadline;i++){
            var item=columns.poll(used,p.columnsPerPlayerTick());if(item==null)break;
            var c=item.value();
            if(!valid(item.peer(),c.context()))continue;
            if(!ServerDownloads.allowed() || !ServerTickGuard.accepting() || !VSSServerConfig.CONFIG.enabled || System.nanoTime()-item.enqueuedNanos()>10_000_000_000L){
                reply(c.context(),new BatchResponseS2CPayload(new byte[]{3},new int[]{c.id()},1));continue;
            }
            var player=(ServerPlayer)c.context().player();
            int x=dev.xantha.vss.common.PositionUtil.unpackX(c.position()),z=dev.xantha.vss.common.PositionUtil.unpackZ(c.position());
            long distance=Math.max(Math.abs((long)x-(player.getBlockX()>>4)),Math.abs((long)z-(player.getBlockZ()>>4)));
            if(distance>(long)VSSServerConfig.CONFIG.effectiveColumnSyncDistanceChunks()+32 || me.cortex.voxy.hxz.compat.SablePlotGuard.excludes(player.serverLevel(),x,z)){
                reply(c.context(),new BatchResponseS2CPayload(new byte[]{0},new int[]{c.id()},1));continue;
            }
            var packet=new BatchChunkRequestC2SPayload(new int[]{c.id()},new long[]{c.position()},new long[]{c.timestamp()},
                new boolean[]{c.generation() && (!p.enabled() || p.allowLodGeneration())},new boolean[]{c.probe()},1);
            ServerTickGuard.run("column request",()->VSSServerNetworking.handleBatchRequest(packet,c.context()));
        }
        if(server.getTickCount()%20==0)synchronized(peers){
            var it=peers.keySet().iterator();while(it.hasNext()){var c=it.next();if(!c.isConnected()){columns.forget(c);controls.forget(c);presence.forget(c);it.remove();}}
        }
    }
    private static boolean valid(Connection c,IPayloadContext context){return c.isConnected() && context.player() instanceof ServerPlayer player && player.connection.getConnection()==c;}
    private static void reply(IPayloadContext context,CustomPacketPayload payload){
        try{if(context.connection().isConnected())context.reply(payload);}catch(RuntimeException ignored){/* A disconnected peer must not break admission. */}
    }
    private static void dispatch(CustomPacketPayload payload,IPayloadContext context){
        if(!valid(context.connection(),context))return;
        if(!ServerDownloads.allowed()&&(payload instanceof BatchChunkRequestC2SPayload||payload instanceof RegionPresenceC2SPayload))return;
        if(payload instanceof HandshakeC2SPayload p)VSSServerNetworking.handleHandshake(p,context);
        else if(payload instanceof BatchChunkRequestC2SPayload p)legacyBatch(p,context);
        else if(payload instanceof CancelRequestC2SPayload p)VSSServerNetworking.handleCancel(p,context);
        else if(payload instanceof BandwidthUpdateC2SPayload p)VSSServerNetworking.handleBandwidthUpdate(p,context);
        else if(payload instanceof RegionPresenceC2SPayload p)VSSServerNetworking.handleRegionPresence(p,context);
        else if(payload instanceof LostCityHintsC2SPayload p)VSSServerNetworking.handleLostCityHints(p,context);
    }
    private static void legacyBatch(BatchChunkRequestC2SPayload batch,IPayloadContext context){
        if(!ServerSafetyPolicy.filterSablePlots){VSSServerNetworking.handleBatchRequest(batch,context);return;}
        var level=((ServerPlayer)context.player()).serverLevel();
        int n=batch.count(),kept=0,rejected=0;
        int[] ids=new int[n],refused=new int[n];long[] positions=new long[n],timestamps=new long[n];boolean[] generation=new boolean[n],probe=new boolean[n];
        for(int i=0;i<n;i++){
            long pos=batch.packedPositions()[i];
            if(me.cortex.voxy.hxz.compat.SablePlotGuard.excludes(level,dev.xantha.vss.common.PositionUtil.unpackX(pos),dev.xantha.vss.common.PositionUtil.unpackZ(pos))){refused[rejected++]=batch.requestIds()[i];continue;}
            ids[kept]=batch.requestIds()[i];positions[kept]=pos;timestamps[kept]=batch.clientTimestamps()[i];generation[kept]=batch.allowGeneration()[i];probe[kept++]=batch.cacheProbe()[i];
        }
        if(rejected==0){VSSServerNetworking.handleBatchRequest(batch,context);return;}
        reply(context,new BatchResponseS2CPayload(new byte[rejected],Arrays.copyOf(refused,rejected),rejected));
        if(kept>0)VSSServerNetworking.handleBatchRequest(new BatchChunkRequestC2SPayload(Arrays.copyOf(ids,kept),Arrays.copyOf(positions,kept),Arrays.copyOf(timestamps,kept),Arrays.copyOf(generation,kept),Arrays.copyOf(probe,kept),kept),context);
    }
    public static void forget(Connection c){synchronized(peers){peers.remove(c);columns.forget(c);controls.forget(c);presence.forget(c);}}
    public static void changedDimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event){
        if(event.getEntity() instanceof ServerPlayer player)forget(player.connection.getConnection());
    }
    public static void clear(){synchronized(peers){peers.clear();columns.clear();controls.clear();presence.clear();}}
    public static int queued(){return columns.size();}
    public static long accepted(){return accepted.sum();}
    public static long rejected(){return rejected.sum();}
}
