import me.cortex.voxy.hxz.core.LodIngestBudget;

public final class LodIngestBudgetTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args) {
        var budget=new LodIngestBudget();
        check(LodIngestBudget.sectionHighWater(1)==256,"single worker floor");
        check(LodIngestBudget.sectionHighWater(4)==512,"real service worker scaling");
        check(LodIngestBudget.sectionHighWater(Integer.MAX_VALUE)==1024,"bounded high-end backlog");
        check(!budget.pauseDrain(255,2),"premature pause");
        check(budget.pauseDrain(256,2),"high watermark must pause");
        check(budget.pauseDrain(129,2),"hysteresis must prevent near-threshold oscillation");
        check(!budget.pauseDrain(128,2),"half watermark must resume");
        check(budget.pauseRequests(128,2,0,0),"new work must stop ahead of drain stop");
        check(budget.pauseRequests(65,2,0,0),"request hysteresis");
        check(!budget.pauseRequests(64,2,0,0),"request recovery");
        check(budget.pauseRequests(0,2,0,24L*1024*1024),"byte pressure hidden by small column count");
        check(budget.pauseRequests(0,2,0,16L*1024*1024+1),"byte-pressure hysteresis");
        check(!budget.pauseRequests(0,2,0,16L*1024*1024),"byte pressure must recover");
        check(budget.pauseRequests(0,2,768,0),"count pressure");
        check(!budget.pauseDrain(0,2),"compressed queue pressure must not stop its own drain");
        budget.reset();
        check(!budget.pauseRequests(65,2,0,0),"old session request latch survived reset");
        check(!budget.pauseDrain(129,2),"old session drain latch survived reset");
        System.out.println("PASS ingest policy: service-worker bounds, early request pause, independent byte/count pressure, hysteresis, reset, drain liveness");
    }
}
