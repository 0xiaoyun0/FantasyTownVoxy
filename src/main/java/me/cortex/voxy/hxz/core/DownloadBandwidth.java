package me.cortex.voxy.hxz.core;

import java.util.*;

/** Atomic global/personal byte reservations; no sleeps and no queues of payloads. */
public final class DownloadBandwidth<K> {
    private static final class Bucket {
        double bytes;long at,rate,largeAt;int waitingPacket;
        Bucket(long now){at=now;}
    }
    private record Peer(Bucket bucket) {}
    private record Claim(int bytes,long seen) {}
    private final Map<K,Peer> peers=new HashMap<>();
    private final LinkedHashMap<K,Claim> claims=new LinkedHashMap<>();
    private double reservedCredit;
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
        long elapsed=Math.max(0,now-total.at);
        refill(total,totalRate,bytes,now);
        if(personal!=null)refill(personal,personalRate,bytes,now);
        advanceClaims(now,elapsed,totalRate);
        boolean personalReady=personal==null||personal.bytes>=bytes;
        // A continuously busy small-packet peer otherwise drains every refill
        // before a different player's indivisible column can earn enough credit.
        // Reserve half of new global credit for the oldest ready large packet;
        // the other half remains usable by other players and acknowledgements.
        if(personalReady&&bytes>normalCapacity(totalRate)&&(claims.containsKey(player)||claims.size()<4096))
            claims.put(player,new Claim(bytes,now));
        var head=head();
        boolean ownsReservation=head!=null&&Objects.equals(head.getKey(),player)&&bytes>=head.getValue().bytes();
        if(!personalReady||total.bytes-(ownsReservation?0:reservedCredit)<bytes)return false;
        spend(total,bytes);if(personal!=null)spend(personal,bytes);
        var claim=claims.get(player);
        if(claim!=null&&bytes>=claim.bytes()){
            claims.remove(player);if(ownsReservation)reservedCredit=0;
        }
        return true;
    }
    private Map.Entry<K,Claim> head(){return claims.isEmpty()?null:claims.entrySet().iterator().next();}
    private void advanceClaims(long now,long elapsed,long rate){
        var head=head();
        while(head!=null&&now-head.getValue().seen()>1_000_000_000L){
            claims.remove(head.getKey());reservedCredit=0;head=head();
        }
        reservedCredit=head==null?0:Math.min(total.bytes,Math.min(head.getValue().bytes(),
                reservedCredit+Math.min(elapsed,1_000_000_000L)/1e9*Math.max(1,rate)*.5));
    }
    private static long normalCapacity(long rate){return Math.min(65536L,Math.max(1L,rate/4));}
    private static void spend(Bucket bucket,int bytes){
        bucket.bytes-=bytes;
        if(bytes>=bucket.waitingPacket)bucket.waitingPacket=0;
    }
    private static void refill(Bucket bucket,long rate,int packet,long now){
        // Quarter-second burst at most 64 KiB; a larger indivisible packet must
        // earn its full credit first. Idle time cannot build a huge send burst.
        long normalCapacity=normalCapacity(rate);
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
    public synchronized void clear(){peers.clear();claims.clear();reservedCredit=0;total=null;cleanup=0;}
}
