import me.cortex.voxy.hxz.core.DownloadBandwidth;

/** Continuous small columns must not starve a different player's larger column. */
public class DownloadFairnessTest {
    public static void main(String[] args){
        var budget=new DownloadBandwidth<String>();long small=0,large=0;int largePackets=0;
        for(int tick=0;tick<1200;tick++){
            long now=tick*50_000_000L;
            if(budget.take("large",131072,65536,65536,now)){large+=131072;largePackets++;}
            for(int packet=0;packet<8;packet++)if(budget.take("small",1024,65536,65536,now))small+=1024;
            if(small+large>now/1e9*65536+16384)throw new AssertionError("Exceeded earned global bandwidth");
        }
        if(largePackets<8||small<1_000_000)throw new AssertionError("Continuous two-player transfer starved: largePackets="+largePackets+", smallBytes="+small);
        System.out.println("PASS mixed packet fairness: largePackets="+largePackets+", smallBytes="+small);
        var multiple=new DownloadBandwidth<Integer>();long total=0;int[] sent=new int[3];
        for(int tick=0;tick<2400;tick++){
            long now=tick*50_000_000L;
            for(int peer=0;peer<3;peer++)if(multiple.take(peer,131072,65536,65536,now)){sent[peer]++;total+=131072;}
            for(int packet=0;packet<8;packet++)if(multiple.take(3,1024,65536,65536,now))total+=1024;
            if(total>now/1e9*65536+16384)throw new AssertionError("Multiple claimants exceeded global bandwidth");
        }
        for(int n:sent)if(n<6)throw new AssertionError("Large claimant starved: "+java.util.Arrays.toString(sent));
        var abandoned=new DownloadBandwidth<Integer>();long resumed=0;
        for(int tick=0;tick<200;tick++){
            long now=tick*50_000_000L;
            if(tick<50)abandoned.take(0,1048576,65536,0,now);
            for(int p=0;p<8;p++)if(abandoned.take(1,1024,65536,0,now)&&tick>=80)resumed+=1024;
        }
        if(resumed<350000)throw new AssertionError("Disconnected claim kept reservation: "+resumed);
        System.out.println("PASS multiple large claimants "+java.util.Arrays.toString(sent)+"; expired claimant releases bandwidth");
    }
}
