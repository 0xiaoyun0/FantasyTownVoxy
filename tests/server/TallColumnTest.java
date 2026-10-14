import dev.xantha.vss.common.processing.EncodedColumnData;
import dev.xantha.vss.networking.payloads.VoxelColumnS2CPayload;

/** Replays the GeneratedColumnFlusher constructor with the report's 65-section column. */
public class TallColumnTest {
    public static void main(String[] args)throws Exception {
        int[] ys=new int[65];for(int i=0;i<ys.length;i++)ys[i]=i-4;
        var data=new EncodedColumnData(0,0,0,1,new byte[]{0},1L,5,true,ys,new int[0],0);
        var payload=new VoxelColumnS2CPayload(1,null,data);
        if(payload.replacementSectionYs().length!=65)throw new AssertionError("Tall column was truncated");
        var sanitize=VoxelColumnS2CPayload.class.getDeclaredMethod("sanitizeReplacementSections",int[].class);sanitize.setAccessible(true);
        for(int[] invalid:new int[][]{{1,1},{128},{-129},new int[256]}){
            try{sanitize.invoke(null,(Object)invalid);throw new AssertionError("Invalid section list accepted");}
            catch(java.lang.reflect.InvocationTargetException expected){if(!(expected.getCause() instanceof IllegalArgumentException))throw expected;}
        }
        for(int count:new int[]{65,128,255}){
            int prefix=count<128?1:2;byte[] raw=new byte[prefix+count];raw[0]=(byte)(count<128?count:(count&127)|128);if(prefix==2)raw[1]=1;
            int[] reversed=new int[count],lengths=new int[count];
            for(int i=0;i<count;i++){reversed[i]=count-i-129;lengths[i]=1;raw[prefix+i]=(byte)reversed[i];}
            var loaded=new dev.xantha.vss.common.processing.LoadedColumnData(0,0,raw,raw.length,true,reversed,lengths);
            for(int i=0;i<count;i++)if(loaded.sectionBytes()[prefix+i]!=(byte)(i-128))throw new AssertionError("VarInt manifest/body sorting disagrees at "+count);
        }
        System.out.println("PASS: GeneratedColumnFlusher 65-section constructor retains every section");
    }
}
