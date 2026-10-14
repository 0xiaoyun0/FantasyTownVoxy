package me.cortex.voxy.hxz.server;

import java.util.*;

/** Bounded, deduplicated mailboxes. Producers never wait for work to finish. */
public final class FairWorkQueue<K,V> {
    public record Work<K,V>(K peer, long id, V value, long enqueuedNanos) {}
    private final LinkedHashMap<K,LinkedHashMap<Long,Work<K,V>>> peers=new LinkedHashMap<>();
    private int size;
    public synchronized boolean offer(K peer,long id,V value,long now,int globalLimit,int peerLimit) {
        var mailbox=peers.get(peer);
        if(mailbox!=null && mailbox.containsKey(id))return false;
        if(size>=globalLimit || mailbox!=null && mailbox.size()>=peerLimit)return false;
        if(mailbox==null){mailbox=new LinkedHashMap<>();peers.put(peer,mailbox);}
        mailbox.put(id,new Work<>(peer,id,value,now));size++;return true;
    }
    public synchronized Work<K,V> poll(Map<K,Integer> used,int peerLimit) {
        var iterator=peers.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();
            if(used.getOrDefault(entry.getKey(),0)>=peerLimit)continue;
            K peer=entry.getKey();var mailbox=entry.getValue();
            var item=mailbox.entrySet().iterator();var work=item.next().getValue();item.remove();size--;
            iterator.remove();if(!mailbox.isEmpty())peers.put(peer,mailbox);
            used.merge(peer,1,Integer::sum);return work;
        }
        return null;
    }
    public synchronized void remove(K peer,long id){var q=peers.get(peer);if(q!=null){if(q.remove(id)!=null)size--;if(q.isEmpty())peers.remove(peer);}}
    public synchronized void forget(K peer){var q=peers.remove(peer);if(q!=null)size-=q.size();}
    public synchronized void clear(){peers.clear();size=0;}
    public synchronized int size(){return size;}
    public synchronized boolean contains(K peer,long id){var q=peers.get(peer);return q!=null && q.containsKey(id);}
    public synchronized int size(K peer){var q=peers.get(peer);return q==null?0:q.size();}
}
