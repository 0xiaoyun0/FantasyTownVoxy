package me.cortex.voxy.hxz.core;

import java.util.*;

/** Atomic global/personal byte reservations; no sleeps and no queues of payloads. */
public final class DownloadBandwidth<K> {
    private static final class Bucket {
        double bytes;long at,rate,largeAt;int waitingPacket;
        Bucket(long now){at=now;}
    }
    private record Peer(Bucket bucket) {}
    private final Map<K,Peer> peers=new HashMap<>();
    private Bucket total;
    private long cleanup;
    public synchronized boolean take(K player,int bytes,long totalRate,long personalRate,long now) {
        if(bytes<=0)return true;
        if(total==null)total=new Bucket(now);
        if(now-cleanup>60_000_000_000L){cleanup=now;peers.values().removeIf(p->now-p.bucket.at>600_000_000_000L);}
        Bucket personal=null;
        if(personalRate>0){
            var peer=peers.get(player);
            if(peer==null){if(peers.size()>=4096)return false;peer=new Peer(new Bucket(now));peers.put(player,peer);}
            personal=peer.bucket;
        }
        refill(total,totalRate,bytes,now);
        if(personal!=null)refill(personal,personalRate,bytes,now);
        if(total.bytes<bytes || personal!=null&&personal.bytes<bytes)return false;
        spend(total,bytes);if(personal!=null)spend(personal,bytes);return true;
    }
    private static void spend(Bucket bucket,int bytes){
        bucket.bytes-=bytes;
        if(bytes>=bucket.waitingPacket)bucket.waitingPacket=0;
    }
    private static void refill(Bucket bucket,long rate,int packet,long now){
        // Quarter-second burst at most 64 KiB; a larger indivisible packet must
        // earn its full credit first. Idle time cannot build a huge send burst.
        long normalCapacity=Math.min(65536L,Math.max(1L,rate/4));
        if(bucket.rate>rate){bucket.bytes=Math.min(bucket.bytes,normalCapacity);bucket.waitingPacket=0;}
        bucket.rate=rate;
        if(now-bucket.largeAt>1_000_000_000L)bucket.waitingPacket=0;
        // Tiny acknowledgement packets, including other players' acknowledgements,
        // must not keep clamping away the earned credit for a waiting large column.
        if(packet>normalCapacity&&packet>=bucket.waitingPacket){bucket.waitingPacket=packet;bucket.largeAt=now;}
        // Leave the ordinary short burst beside the large packet reservation:
        // acknowledgements are visited first by VSS and must not always consume
        // its last few bytes immediately before the column is considered.
        double capacity=normalCapacity+(double)bucket.waitingPacket;
        long elapsed=Math.max(0,now-bucket.at);bucket.at=now;
        bucket.bytes=Math.min(capacity,bucket.bytes+Math.min(elapsed,600_000_000_000L)/1e9*Math.max(1L,rate));
    }
    public synchronized void clear(){peers.clear();total=null;cleanup=0;}
}
