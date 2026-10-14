import me.cortex.voxy.hxz.core.*;
public class CoreTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    static FrameGovernor run(boolean adaptive,boolean progressive,int fps,int seconds){var g=new FrameGovernor();for(int i=0;i<=fps*seconds;i++)g.update(i*1_000_000_000L/fps,16,30,.3,0,5,.25,adaptive,progressive);return g;}
    public static void main(String[] args){
        check(run(false,false,60,1).distance()==16,"disabled controller uses requested distance immediately");
        check(run(false,true,60,4).distance()==1,"startup warmup remains bounded");
        double a=run(false,true,30,30).distance(),b=run(false,true,144,30).distance();
        check(Math.abs(a-b)<.3,"progressive loading independent of render FPS");
        check(run(true,false,15,10).distance()<4,"adaptive pressure shrinks distance with ramp disabled");
        check(run(false,true,15,30).distance()>6,"ramp can operate without FPS feedback");
        check(run(true,true,60,120).distance()==16,"healthy frame rate reaches configured target");
        for(int cap:new int[]{30,60,120}){
            var capped=new FrameGovernor();
            for(int i=0;i<=cap*180;i++)capped.update(i*1_000_000_000L/cap,16,cap,.3,0,5,.25,true,true);
            check(capped.distance()==16,"stable "+cap+" FPS at the configured target still probes toward requested distance");
        }
        var g=run(true,true,60,20);double before=g.distance();g.update(1000_000_000_000L,16,30,.2,0,5,.25,true,true);
        check(g.distance()==before,"long pauses never create a catch-up burst");
        var budget=new WorkBudget();budget.take(0,120,8);check(budget.take(100_000_000_000L,120,8)<=8,"work budget burst stays bounded");
        for(int fps:new int[]{30,60,144}){
            var visible=new WorkBudget();var hidden=new WorkBudget();int warmed=0,total=0;
            for(int i=0;i<=fps*5;i++){
                long now=i*1_000_000_000L/fps;
                var allowed=WorkBudget.allocate(50,visible.take(now,120,8),hidden.take(now,4,2),true);
                warmed+=allowed.background();total+=allowed.total();
                if(allowed.total()>50)throw new AssertionError("GPU queue overflow");
            }
            check(warmed>=19 && warmed<=20 && total<=620,"background allowance remains active and bounded at "+fps+" FPS");
        }
        SessionNamespaces.beginConfiguration();SessionNamespaces.offer("1234ABCD",false,"survival");String one=SessionNamespaces.beginJoin();
        SessionNamespaces.beginConfiguration();SessionNamespaces.offer("1234ABCD",false,"creative");String two=SessionNamespaces.beginJoin();
        check(!one.equals(two),"same seed/server identity isolates independent backends");
        SessionNamespaces.beginConfiguration();String fallback=SessionNamespaces.beginJoin();long joined=SessionNamespaces.epoch();
        SessionNamespaces.endSession();SessionNamespaces.beginConfiguration();
        check(fallback.equals(SessionNamespaces.beginJoin()),"server-address-scoped disk cache survives reconnect without a handshake");
        check(joined!=SessionNamespaces.epoch(),"stable disk identity does not reuse async session epochs");
        SessionNamespaces.beginConfiguration();SessionNamespaces.offer("1234ABCD",false,"survival");
        check(one.equals(SessionNamespaces.beginJoin()),"same VSS backend keeps its saved cache on rejoin");
        SessionNamespaces.beginConfiguration();SessionNamespaces.offer("1234ABCD",false,"creative");SessionNamespaces.beginConfiguration();
        check(fallback.equals(SessionNamespaces.beginJoin()),"a new configuration cannot inherit stale VSS backend identity");
        var epoch=new TaskEpoch();long old=epoch.capture();epoch.advance();check(!epoch.current(old)&&epoch.current(epoch.capture()),"late tasks cannot retain a new session token");
        check(!TerrainFingerprint.digest("ab",new byte[]{99}).equals(TerrainFingerprint.digest("a",new byte[]{98,99})),"terrain fingerprint delimits metadata from generator bytes");
        var recovery=run(true,true,60,120);double healthy=recovery.distance();
        for(int i=1;i<=60*10;i++)recovery.update(120_000_000_000L+i*1_000_000_000L/60,16,30,.90,0,5,.25,true,true);
        double pressured=recovery.distance();
        check(pressured<healthy&&recovery.meshRequestsPerSecond(360)<=8,"heap pressure reduces distance and high-end request budget");
        for(int i=1;i<=60*180;i++)recovery.update(130_000_000_000L+i*1_000_000_000L/60,16,30,.30,0,5,.25,true,true);
        check(recovery.distance()==16,"controller recovers after memory pressure clears");
    }
}
