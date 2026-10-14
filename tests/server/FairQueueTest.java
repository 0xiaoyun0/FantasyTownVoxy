import me.cortex.voxy.hxz.server.FairWorkQueue;
import java.util.*;
import java.util.concurrent.*;

public final class FairQueueTest {
    public static void main(String[] args)throws Exception{
        var q=new FairWorkQueue<Integer,Integer>();
        for(int i=0;i<1000;i++)q.offer(1,i,i,0,512,64);
        check(q.size()==64,"per-player bound");
        check(!q.offer(1,0,0,0,512,64),"duplicate rejected");
        check(q.contains(1,0),"deduplicating keeps the accepted request alive");
        q.offer(2,1,1,0,512,64);var used=new HashMap<Integer,Integer>();
        check(q.poll(used,4).peer()==1,"first player");
        check(q.poll(used,4).peer()==2,"quiet player is not starved");
        while(q.poll(used,4)!=null){}check(used.get(1)==4,"per-tick bound");
        q.forget(1);check(q.size()==0,"disconnect releases queue");
        check(!q.contains(1,0),"dimension/disconnect cleanup removes old identity");
        try(var executor=Executors.newFixedThreadPool(16)){
            var futures=new ArrayList<Future<?>>();
            for(int peer=0;peer<32;peer++){int key=peer;futures.add(executor.submit(()->{
                for(int i=0;i<2000;i++)q.offer(key,i,i,0,512,64);
            }));}
            for(var future:futures)future.get();
        }
        check(q.size()==512,"concurrent global bound");
        for(int peer=0;peer<32;peer++)check(q.size(peer)<=64,"concurrent player bound");
        int drained=0;while(q.size()>0){used.clear();while(q.poll(used,4)!=null)drained++;}
        check(drained==512,"every accepted item drains exactly once");
        check(q.size()==0,"empty queue accounting");
        System.out.println("PASS fair queue: 64000 concurrent offers, global/per-peer caps, deduplication, fair drain, disconnect cleanup");
    }
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
}
