package dev.xantha.vss.networking;

import dev.xantha.vss.networking.payloads.WorldgenProfileFragmentS2CPayload;
import dev.xantha.vss.networking.payloads.WorldgenProfileS2CPayload;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import me.cortex.voxy.hxz.server.ServerSafetyPolicy;
import net.minecraft.network.FriendlyByteBuf;

/** Reuse immutable profile encodings across joins; never reuse transfer IDs or mutable packet arrays. */
public final class WorldgenProfileTransfer {
    private record Encoded(WorldgenProfileS2CPayload profile,int bytes,List<byte[]> fragments){}
    private static final AtomicInteger TRANSFERS=new AtomicInteger();
    private static final LinkedList<Encoded> CACHE=new LinkedList<>();
    private static long cachedBytes,encodings;

    public static void send(WorldgenProfileS2CPayload profile,Consumer<WorldgenProfileFragmentS2CPayload> sender){
        Encoded encoded=encoded(profile);int transfer=TRANSFERS.incrementAndGet(),offset=0;
        for(byte[] part:encoded.fragments){
            sender.accept(new WorldgenProfileFragmentS2CPayload(transfer,encoded.bytes,offset,part.clone()));
            offset+=part.length;
        }
    }
    private static synchronized Encoded encoded(WorldgenProfileS2CPayload profile){
        trim();
        for(var it=CACHE.iterator();it.hasNext();){
            var entry=it.next();
            if(entry.profile==profile){it.remove();CACHE.addLast(entry);return entry;}
        }
        FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer(8192,WorldgenProfileFragmentS2CPayload.MAX_BYTES));
        Encoded result;
        try{
            WorldgenProfileS2CPayload.encode(profile,buf);encodings++;
            int total=buf.readableBytes();var parts=new ArrayList<byte[]>();
            while(buf.isReadable()){
                byte[] part=new byte[Math.min(WorldgenProfileFragmentS2CPayload.CHUNK_BYTES,buf.readableBytes())];
                buf.readBytes(part);parts.add(part);
            }
            result=new Encoded(profile,total,List.copyOf(parts));
        }finally{buf.release();}
        long budget=budget();
        if(result.bytes<=budget){CACHE.addLast(result);cachedBytes+=result.bytes;trim();}
        return result;
    }
    private static long budget(){var p=ServerSafetyPolicy.profileTransfers;return p.enabled()?p.mebibytes()*1048576L:0;}
    public static synchronized void trim(){
        long budget=budget();
        while(!CACHE.isEmpty()&&(cachedBytes>budget||CACHE.size()>4))cachedBytes-=CACHE.removeFirst().bytes;
    }
    public static synchronized void clear(){CACHE.clear();cachedBytes=0;}
    public static synchronized long cachedBytes(){return cachedBytes;}
    public static synchronized long encodingCount(){return encodings;}
    private WorldgenProfileTransfer(){}
}
