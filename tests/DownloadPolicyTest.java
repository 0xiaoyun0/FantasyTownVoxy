import me.cortex.voxy.hxz.core.*;
import java.time.*;
public class DownloadPolicyTest {
    static long at(String time){return LocalDateTime.parse("2026-10-14T"+time).toEpochSecond(ZoneOffset.ofHours(8));}
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    public static void main(String[] args){
        var window=new DailyDownloadWindow(true,17*60,23*60,480);
        check(!window.blocked(at("16:59:59"))&&window.blocked(at("17:00:00"))&&window.blocked(at("22:59:59"))&&!window.blocked(at("23:00:00")),"17:00-inclusive / 23:00-exclusive boundaries");
        var overnight=new DailyDownloadWindow(true,23*60,7*60,480);
        check(overnight.blocked(at("23:30:00"))&&overnight.blocked(at("00:00:00"))&&!overnight.blocked(at("07:00:00")),"cross-midnight");
        check(new DailyDownloadWindow(true,0,0,0).blocked(0)&&!new DailyDownloadWindow(false,0,0,0).blocked(0),"all day and disabled modes");
        var budget=new DownloadBandwidth<Integer>();long[] totals=new long[4];long global=0;
        for(int tick=0;tick<1200;tick++){
            long now=tick*50_000_000L;
            for(int i=0;i<4;i++)for(int packet=0;packet<8;packet++){
                int id=(i+tick)%4;if(budget.take(id,1024,65536,32768,now)){global+=1024;totals[id]+=1024;}
            }
            check(global<=now/1e9*65536+16384,"global rate bypass");
            for(long total:totals)check(total<=now/1e9*32768+8192,"personal rate bypass");
        }
        check(global>3_700_000,"unexpected throughput starvation");
        for(long total:totals)check(total>800_000,"player starvation");
        var low=new DownloadBandwidth<String>();check(!low.take("p",65536,65536,1024,0),"unearned large packet");
        check(low.take("p",65536,65536,1024,64_000_000_000L),"large packet must eventually accumulate full credit");
        check(!low.take("p",65536,65536,1024,64_000_000_001L),"reconnect/instant retry cannot reset UUID credit");
        var change=new DownloadBandwidth<String>();change.take("p",1024,65536,32768,0);
        check(change.take("p",1024,65536,32768,1_000_000_000L),"initial rate");
        check(!change.take("p",8192,65536,1024,1_000_000_001L),"new lower rate clamps stored burst");
        var mixed=new DownloadBandwidth<String>();boolean largeSent=false;
        for(int step=0;step<200;step++){
            long now=step*50_000_000L;
            mixed.take("p",32,65536,32768,now);
            mixed.take("other",32,65536,32768,now);
            if(mixed.take("p",131072,65536,32768,now)){largeSent=true;break;}
        }
        check(largeSent,"small acknowledgements must not erase the credit needed for an indivisible large packet");
        System.out.println("PASS daily/timezone/cross-midnight/exclusive-boundary/off rules; four-player global+personal limits, fairness, large packets, reconnect and hot lower rate");
    }
}
