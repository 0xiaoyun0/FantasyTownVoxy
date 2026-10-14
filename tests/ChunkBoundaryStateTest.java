import java.lang.reflect.*;
import it.unimi.dsi.fastutil.longs.*;
import me.cortex.voxy.client.core.rendering.ChunkBoundRenderer;

/** Exercise real queue methods without allocating graphics resources. */
public class ChunkBoundaryStateTest {
    static Object unsafe() throws Exception {
        var f=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");f.setAccessible(true);return f.get(null);
    }
    static void set(Object o,String n,Object v)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
    static Object get(Object o,String n)throws Exception{var f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    static ChunkBoundRenderer renderer()throws Exception{
        Object u=unsafe();var r=(ChunkBoundRenderer)u.getClass().getMethod("allocateInstance",Class.class).invoke(u,ChunkBoundRenderer.class);
        var indices=new Long2IntOpenHashMap();indices.defaultReturnValue(-1);
        set(r,"chunk2idx",indices);set(r,"addQueue",new LongOpenHashSet());set(r,"remQueue",new LongOpenHashSet());set(r,"idx2chunk",new long[4096]);return r;
    }
    static void check(boolean good,String message){if(!good)throw new AssertionError(message);}
    public static void main(String[]args)throws Exception{
        int failed=0;
        for(String test:new String[]{"last-event","reset","resident","absent","cancel-add"})try{
            var r=renderer();long pos=42;
            if(test.equals("last-event")){
                r.removeSection(pos);r.addSection(pos);
                check(((LongOpenHashSet)get(r,"addQueue")).contains(pos),"visible section lost after remove/add before render; depth mask no longer represents final visibility");
            }else if(test.equals("reset")){
                r.addSection(pos);r.removeSection(81);r.reset();
                check(((LongOpenHashSet)get(r,"addQueue")).isEmpty()&&((LongOpenHashSet)get(r,"remQueue")).isEmpty(),"old world's pending depth-mask geometry survived reset");
            }else if(test.equals("resident")){
                ((Long2IntOpenHashMap)get(r,"chunk2idx")).put(pos,0);
                r.removeSection(pos);r.addSection(pos);r.removeSection(pos);r.addSection(pos);
                check(((LongOpenHashSet)get(r,"addQueue")).isEmpty()&&((LongOpenHashSet)get(r,"remQueue")).isEmpty(),"visible resident section was added twice or removed");
            }else if(test.equals("absent")){
                r.removeSection(pos);r.removeSection(pos);
                check(((LongOpenHashSet)get(r,"remQueue")).isEmpty(),"unseen section queued for removal");
            }else{
                r.addSection(pos);r.removeSection(pos);r.addSection(pos);r.removeSection(pos);
                check(((LongOpenHashSet)get(r,"addQueue")).isEmpty()&&((LongOpenHashSet)get(r,"remQueue")).isEmpty(),"invisible section survived cancelled addition");
            }
            System.out.println("PASS boundary "+test);
        }catch(AssertionError failure){System.out.println("FAIL boundary "+test+": "+failure.getMessage());failed++;}
        if(failed!=0)throw new AssertionError(failed+" boundary state regressions");
    }
}
