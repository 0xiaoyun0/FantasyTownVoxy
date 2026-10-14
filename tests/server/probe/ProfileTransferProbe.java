package hxz.safetyprobe;
import java.util.*;
import dev.xantha.vss.networking.WorldgenProfileTransfer;
import dev.xantha.vss.networking.payloads.*;
import me.cortex.voxy.hxz.server.ServerSafetyPolicy;

public final class ProfileTransferProbe {
    public static void run(){
        var saved=ServerSafetyPolicy.profileTransfers;
        try{
            ServerSafetyPolicy.profileTransfers=new ServerSafetyPolicy.ProfileTransfers(true,4);WorldgenProfileTransfer.clear();
            byte[] registry=new byte[600000];new Random(123).nextBytes(registry);
            var profile=new WorldgenProfileS2CPayload(3,42,1,0,registry.length,registry,List.of());
            var first=new ArrayList<WorldgenProfileFragmentS2CPayload>();long before=WorldgenProfileTransfer.encodingCount();WorldgenProfileTransfer.send(profile,first::add);
            byte original=first.getFirst().data()[0];first.getFirst().data()[0]^=127;
            var next=new ArrayList<WorldgenProfileFragmentS2CPayload>();WorldgenProfileTransfer.send(profile,next::add);
            if(WorldgenProfileTransfer.encodingCount()!=before+1||first.size()!=2||next.size()!=2||next.getFirst().data()[0]!=original||next.getFirst().transfer()==first.getFirst().transfer())throw new AssertionError("Profile reuse, fragment isolation or transfer ID failed");
            for(int i=0;i<12;i++)WorldgenProfileTransfer.send(new WorldgenProfileS2CPayload(3,42,2+i,0,registry.length,registry,List.of()),p->{});
            if(WorldgenProfileTransfer.cachedBytes()>4*600100L)throw new AssertionError("Profile count bound exceeded");
            // A single profile above the configured budget must never be retained.
            registry=new byte[5*1048576];WorldgenProfileTransfer.send(new WorldgenProfileS2CPayload(3,42,100,0,registry.length,registry,List.of()),p->{});
            if(WorldgenProfileTransfer.cachedBytes()>4*1048576L)throw new AssertionError("Profile byte bound exceeded");
            ServerSafetyPolicy.profileTransfers=new ServerSafetyPolicy.ProfileTransfers(false,4);WorldgenProfileTransfer.trim();
            if(WorldgenProfileTransfer.cachedBytes()!=0)throw new AssertionError("Hot-disable did not release cache");
            before=WorldgenProfileTransfer.encodingCount();WorldgenProfileTransfer.send(profile,p->{});WorldgenProfileTransfer.send(profile,p->{});
            if(WorldgenProfileTransfer.encodingCount()!=before+2||WorldgenProfileTransfer.cachedBytes()!=0)throw new AssertionError("Disabled cache still reused profiles");
            System.out.println("HXZ_PROFILE PASS multi-fragment reuse, unique transfers, mutation isolation, version/count/byte bounds and hot-disable");
        }finally{ServerSafetyPolicy.profileTransfers=saved;WorldgenProfileTransfer.clear();}
    }
}
