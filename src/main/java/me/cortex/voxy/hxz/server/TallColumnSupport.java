package me.cortex.voxy.hxz.server;

import java.util.*;
import dev.xantha.vss.networking.server.state.PlayerRequestState;
import dev.xantha.vss.networking.payloads.*;
import dev.xantha.vss.networking.VSSNetworking;
import net.minecraft.server.level.ServerPlayer;

/** The v5 presence/cache indexes store the section count in one unsigned byte. */
public final class TallColumnSupport {
    public static final int CAPABILITY=32,MAX_SECTIONS=255;
    private static final Map<PlayerRequestState,Integer> capabilities=Collections.synchronizedMap(new WeakHashMap<>());
    public static void remember(PlayerRequestState state,int value){capabilities.put(state,value);}
    public static boolean supports(PlayerRequestState state){return (capabilities.getOrDefault(state,0)&CAPABILITY)!=0;}
    public static int[] sanitize(int[] source){
        if(source==null)return new int[0];
        if(source.length>MAX_SECTIONS)throw new IllegalArgumentException("Too many replacement sections: "+source.length);
        int[] copy=source.clone();Arrays.sort(copy);
        for(int i=0;i<copy.length;i++)if(copy[i]<-128||copy[i]>127||i>0&&copy[i]==copy[i-1])throw new IllegalArgumentException("Invalid or duplicate replacement section: "+copy[i]);
        return copy;
    }
    public static byte[] reorder(byte[] bytes,int[] ys,int[] lengths){
        if(bytes==null||ys.length<=1||ys.length!=lengths.length)return bytes;
        int prefix=0,count=0,shift=0;
        do{if(prefix>=bytes.length||prefix>=5)return bytes;int part=bytes[prefix++]&255;count|=(part&127)<<shift;shift+=7;if((part&128)==0)break;}while(true);
        if(count!=ys.length)return bytes;
        long expected=prefix;for(int length:lengths){if(length<=0)return bytes;expected+=length;}
        if(expected!=bytes.length)return bytes;
        int[] starts=new int[count];Integer[] order=new Integer[count];int offset=prefix;
        for(int i=0;i<count;i++){starts[i]=offset;offset+=lengths[i];order[i]=i;}
        Arrays.sort(order,Comparator.comparingInt(i->ys[i]));
        byte[] result=bytes.clone();offset=prefix;
        for(int i:order){System.arraycopy(bytes,starts[i],result,offset,lengths[i]);offset+=lengths[i];}
        return result;
    }
    public static boolean skipLegacy(ServerPlayer player,PlayerRequestState state,VoxelColumnS2CPayload payload){
        if(payload.replacementSectionYs().length<=64||supports(state))return false;
        // Old protocol-50 clients cannot decode tall columns. Complete with an ordinary unavailable response.
        state.clearRequest(payload.requestId());
        VSSNetworking.sendToPlayer(player,new BatchResponseS2CPayload(new byte[]{2},new int[]{payload.requestId()},1));
        return true;
    }
}
