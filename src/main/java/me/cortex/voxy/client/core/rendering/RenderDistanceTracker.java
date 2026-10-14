package me.cortex.voxy.client.core.rendering;
import me.cortex.voxy.client.core.util.*;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.hxz.HxzSettings;
import me.cortex.voxy.hxz.client.HxzClient;
import java.util.function.LongConsumer;
public class RenderDistanceTracker {
    private final LongConsumer addTopLevelNode,removeTopLevelNode;
    private final int processRate,minSec,maxSec;
    private final boolean near;
    private RingTracker original;
    private HxzRingTracker ordered;
    private int renderDistance=2;
    private double posX,posZ;
    private boolean centered;
    public RenderDistanceTracker(int rate,int minSec,int maxSec,LongConsumer add,LongConsumer remove){
        this.processRate=rate;this.minSec=minSec;this.maxSec=maxSec;this.addTopLevelNode=add;this.removeTopLevelNode=remove;
        var s=HxzSettings.CONFIG;near=s.scheduling()&&s.nearFirstLoading;
        if(near)ordered=new HxzRingTracker(2,0,0,true);else original=new RingTracker(2,0,0,true);
    }
    public void setRenderDistance(int distance){if(distance==renderDistance)return;renderDistance=distance;
        if(near){ordered.unload();ordered=new HxzRingTracker(ordered,distance,((int)Math.floor(posX))>>9,((int)Math.floor(posZ))>>9,true);}
        else {original.unload();original=new RingTracker(original,distance,((int)posX)>>9,((int)posZ)>>9,true);}
    }
    public boolean setCenterAndProcess(double x,double z){double dx=posX-x,dz=posZ-z;
        if(near&&!centered||dx*dx+dz*dz>16384){centered=true;posX=x;posZ=z;
            if(near)ordered.moveCenter(((int)Math.floor(x))>>9,((int)Math.floor(z))>>9);else original.moveCenter(((int)x)>>9,((int)z)>>9);}
        int budget=HxzClient.columnUpdates(processRate);
        return (near?ordered.process(budget,this::add,this::rem):original.process(budget,this::add,this::rem))!=0;
    }
    private void add(int x,int z){for(int y=minSec;y<=maxSec;y++)addTopLevelNode.accept(WorldEngine.getWorldSectionId(4,x,y,z));}
    private void rem(int x,int z){for(int y=minSec;y<=maxSec;y++)removeTopLevelNode.accept(WorldEngine.getWorldSectionId(4,x,y,z));}
}
