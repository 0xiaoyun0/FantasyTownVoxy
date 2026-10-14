package me.cortex.voxy.hxz.importer;

import java.nio.*;
import com.github.luben.zstd.Zstd;

/** Version-0 SaveLoadSystem3 records shared by the supplied Voxy 0.2.15 and NeoVoxy. */
public record LodRecord(long key,short[] indices,long[] palette) {
    public static final int HEADER=16, VOLUME=32768, MAX_BYTES=HEADER+VOLUME*10;
    public static LodRecord decode(long expected,byte[] stored) {
        if(stored.length<4||stored.length>MAX_BYTES+4096)throw new IllegalArgumentException("LOD 记录大小异常");
        byte[] raw=stored;
        if(stored[0]==0x28 && stored[1]==(byte)0xb5 && stored[2]==0x2f && stored[3]==(byte)0xfd){
            raw=new byte[MAX_BYTES];
            long size=Zstd.decompressByteArray(raw,0,raw.length,stored,0,stored.length);
            if(Zstd.isError(size))throw new IllegalArgumentException("LOD 压缩数据损坏");
            raw=java.util.Arrays.copyOf(raw,(int)size);
        }
        if(raw.length<HEADER+VOLUME*2+8)throw new IllegalArgumentException("LOD 记录被截断");
        ByteBuffer b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        long key=b.getLong(),metadata=b.getLong();
        int count=(int)(metadata&65535);
        if(key!=expected||(key&15)!=0||(key>>>60)>4||metadata>>>24!=0||count<1||count>VOLUME||raw.length!=HEADER+VOLUME*2+count*8)
            throw new IllegalArgumentException("不支持的 LOD 格式或记录头损坏");
        short[] indices=new short[VOLUME];
        for(int i=0;i<VOLUME;i++){indices[i]=b.getShort();if(Short.toUnsignedInt(indices[i])>=count)throw new IllegalArgumentException("LOD 调色板索引越界");}
        long[] palette=new long[count];for(int i=0;i<count;i++)palette[i]=b.getLong();
        return new LodRecord(key,indices,palette);
    }
    public long at(int x,int y,int z){return palette[Short.toUnsignedInt(indices[(y<<10)|(z<<5)|x])];}
}
