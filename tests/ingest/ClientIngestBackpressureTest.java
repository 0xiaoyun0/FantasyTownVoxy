package dev.xantha.vss.networking.client;

import java.lang.reflect.*;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.thread.ServiceManager;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.VoxyInstance;
import me.cortex.voxy.commonImpl.WorldIdentifier;

/** Actual processor queue and actual Voxy pending-work signal; no game, GL or worker threads. */
public final class ClientIngestBackpressureTest {
    static final class TestInstance extends VoxyInstance {
        protected SectionStorage createStorage(WorldIdentifier id) { throw new AssertionError("unexpected storage access"); }
    }
    static Field field(Class<?> type,String name)throws Exception {var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    static Object get(Object value,String name)throws Exception {return field(value.getClass(),name).get(value);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static Object invoke(Object value,String name)throws Exception {
        if(name.equals("pollQueuedColumn")) {
            try {
                var m=value.getClass().getDeclaredMethod(name,int.class);m.setAccessible(true);
                return m.invoke(value,((AtomicInteger)get(value,"sessionEpoch")).get());
            } catch(NoSuchMethodException baseline) { /* Older releases have no epoch argument. */ }
        }
        var m=value.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(value);
    }
    static TestInstance install(VoxelIngestService service)throws Exception {
        Class<?> u=Class.forName("sun.misc.Unsafe");Object unsafe=field(u,"theUnsafe").get(null);
        TestInstance instance=(TestInstance)u.getMethod("allocateInstance",Class.class).invoke(unsafe,TestInstance.class);
        field(VoxyInstance.class,"ingestService").set(instance,service);
        field(VoxyCommon.class,"INSTANCE").set(null,instance);
        return instance;
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        var service=new VoxelIngestService(new ServiceManager(count -> {}));
        Object serviceState=get(service,"service");
        var pending=(Semaphore)get(serviceState,"tasks");
        install(service);
        var processor=new ClientColumnProcessor();processor.beginSession();
        var priority=(Queue<Object>)get(processor,"priorityColumnQueue");
        var normal=(Queue<Object>)get(processor,"columnQueue");
        Object first=new ClientColumnTransferAssembler.AssembledColumn(1,1L,2,3,null,1L,true,false,List.of(),64);
        Object second=new ClientColumnTransferAssembler.AssembledColumn(2,2L,4,5,null,1L,false,false,List.of(),96);
        priority.add(first);normal.add(second);
        ((AtomicInteger)get(processor,"priorityQueueSize")).set(1);
        ((AtomicInteger)get(processor,"queueSize")).set(2);
        ((AtomicLong)get(processor,"queueBytes")).set(160);
        pending.release(8192);
        check(service.getTaskCount()==8192,"fixture must report real Voxy ingest backlog");
        check(invoke(processor,"pollQueuedColumn")==null,
                "decoder removed a retained column while actual Voxy ingest backlog is 8192; decode can keep feeding an unbounded downstream queue");
        check(priority.size()==1&&normal.size()==1,"paused columns were dropped");
        check(((AtomicInteger)get(processor,"priorityQueueSize")).get()==1,"pause consumed priority accounting");
        check(((AtomicInteger)get(processor,"queueSize")).get()==2&&((AtomicLong)get(processor,"queueBytes")).get()==160,
                "pause changed retained queue accounting before dispatch");
        pending.drainPermits();
        check(invoke(processor,"pollQueuedColumn")==first,"recovery did not retain priority ordering");
        check(invoke(processor,"pollQueuedColumn")==second,"recovery lost normal column");
        pending.release(8192);normal.add(second);
        me.cortex.voxy.hxz.HxzSettings.CONFIG.clientIngestBackpressure=false;
        check(invoke(processor,"pollQueuedColumn")==second,"opt-out did not restore original drain");
        me.cortex.voxy.hxz.HxzSettings.CONFIG.clientIngestBackpressure=true;
        Class<?> bridge=Class.forName("dev.xantha.vss.networking.client.ClientLodBackpressure");
        var requestPause=bridge.getMethod("pauseRequests");
        field(ClientColumnProcessor.class,"activeProcessor").set(null,processor);
        pending.drainPermits();
        ((AtomicInteger)get(processor,"queueSize")).set(1);
        ((AtomicLong)get(processor,"queueBytes")).set(24L*1024*1024);
        check((boolean)requestPause.invoke(null),"request bridge ignored actual decode bytes");
        ((AtomicLong)get(processor,"queueBytes")).set(0);
        check(!(boolean)requestPause.invoke(null),"request bridge did not recover after compressed queue drained");
        normal.add(second);pending.release(8192);
        check(invoke(processor,"pollQueuedColumn")==null,"fixture did not latch pause before world reset");
        processor.clearPendingTransfers();
        check(normal.isEmpty()&&priority.isEmpty(),"world reset retained old queued columns");
        check(((AtomicInteger)get(processor,"queueSize")).get()==0&&((AtomicLong)get(processor,"queueBytes")).get()==0,
                "world reset retained queue accounting");
        pending.drainPermits();pending.release(129);normal.add(second);
        check(invoke(processor,"pollQueuedColumn")==second,"world reset retained previous pause hysteresis");
        processor.shutdown();
        check(field(ClientColumnProcessor.class,"activeProcessor").get(null)==null,"disconnect retained the queue owner");
        field(VoxyCommon.class,"INSTANCE").set(null,null);
        processor.beginSession();
        normal.add(second);
        check(invoke(processor,"pollQueuedColumn")==second,"missing Voxy instance retained stale pause");
        processor.shutdown();
        System.out.println("PASS actual client processor: downstream pressure retains both queues/accounting, resumes in order, opt-out and lifecycle reset");
    }
}
