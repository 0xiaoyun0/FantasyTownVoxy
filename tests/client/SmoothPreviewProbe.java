package hxz.smoke;

import dev.xantha.vss.client.prediction.*;
import me.cortex.voxy.hxz.HxzSettings;

/** Native production mesher and mixin, including packing, switch and wet-bank geometry. */
public final class SmoothPreviewProbe {
    private static ClientColumnSample sample(int height,int water){
        int n=ClientColumnSample.NO_SPAN,b=ClientColumnSample.NO_BLOCK;
        return new ClientColumnSample(height,water,0,b,0,0,0,0,water>height?1:0,
                ClientColumnSample.FLAG_SURFACE_ONLY|ClientColumnSample.FLAG_APPROXIMATE,0,b,b,n,n,n,n);
    }
    private static PredictionQuadMesh mesh(ClientColumnSample[] samples,int step)throws Exception{
        var resultType=Class.forName("dev.xantha.vss.client.prediction.PredictionSimpleVegetation$Result");
        var empty=resultType.getDeclaredField("EMPTY");empty.setAccessible(true);
        var method=java.util.Arrays.stream(PredictionMeshBuilder.class.getDeclaredMethods()).filter(m->m.getName().equals("buildForRenderingWithCityPreview")).findFirst().orElseThrow();
        method.setAccessible(true);
        return ((PredictionMesh)method.invoke(null,samples,null,64,0xff3377aa,step,3,
                null,null,0,0,null,empty.get(null),null,(java.util.function.BooleanSupplier)()->true)).packed();
    }
    private static float y(PredictionQuadMesh mesh,int quad,int corner)throws Exception{
        var method=PredictionQuadMesh.class.getDeclaredMethod("y",int.class,int.class);method.setAccessible(true);
        return (float)method.invoke(mesh,quad,corner);
    }
    public static void verify()throws Exception{
        boolean original=HxzSettings.CONFIG.smoothPredictionPreview;
        boolean quality=HxzSettings.CONFIG.qualityFirstLod;
        try{
            HxzSettings.CONFIG.qualityFirstLod=false;
            var samples=new ClientColumnSample[9];
            for(int z=0;z<3;z++)for(int x=0;x<3;x++)samples[z*3+x]=sample(60+x*20+z*10,0);
            HxzSettings.CONFIG.smoothPredictionPreview=true;
            var smooth=mesh(samples,64);int first=smooth.quadForCell(0),next=smooth.quadForCell(1);
            if(y(smooth,first,0)!=60||y(smooth,first,1)!=80||y(smooth,first,2)!=90||y(smooth,first,3)!=70)
                throw new AssertionError("Coarse surface became a flat block instead of joining its four samples");
            if(y(smooth,first,1)!=y(smooth,next,0)||y(smooth,first,2)!=y(smooth,next,3))throw new AssertionError("Adjacent coarse cells have a crack");
            if(smooth.quadCount()!=4)throw new AssertionError("Smooth preview retained giant vertical walls");
            var s=samples[0];
            samples[0]=new ClientColumnSample(s.surfaceY(),s.fluidY(),s.biomeIndex(),s.topBlockIndex(),0,0,0,0,0,
                    s.flags()|ClientColumnSample.FLAG_CAPTURED,0,s.underBlockIndex(),s.deepBlockIndex(),50,40,20,0);
            var capturedCave=mesh(samples,64);
            if(y(capturedCave,capturedCave.quadForCell(0),1)!=80||capturedCave.quadCount()!=4)
                throw new AssertionError("One captured cave turned the entire coarse tile into giant columns");
            samples[0]=s;
            var fine=mesh(samples,1);
            if(y(fine,fine.quadForCell(0),0)!=y(fine,fine.quadForCell(0),1))throw new AssertionError("Fine block geometry was smoothed");
            HxzSettings.CONFIG.smoothPredictionPreview=false;
            var legacy=mesh(samples,64);
            if(y(legacy,legacy.quadForCell(0),0)!=y(legacy,legacy.quadForCell(0),1))throw new AssertionError("Opt-out failed to restore VSS columns");
            HxzSettings.CONFIG.smoothPredictionPreview=true;
            for(int z=0;z<3;z++)for(int x=0;x<3;x++)samples[z*3+x]=sample(50+x*20,64);
            var wet=mesh(samples,64);int water=wet.waterQuadForCell(0);
            var waterY=PredictionQuadMesh.class.getDeclaredMethod("waterY",int.class,int.class);waterY.setAccessible(true);
            if(water<0||Math.abs((float)waterY.invoke(wet,water,0)-(64-(1.001F-8.0F/9.0F)))>.0001)
                throw new AssertionError("Sloped shoreline changed water elevation");
            System.out.println("HXZ_SMOOTH PASS actual mesher: slopes, shared edges, no giant walls, fine blocks, opt-out, water elevation");
        }finally{HxzSettings.CONFIG.smoothPredictionPreview=original;HxzSettings.CONFIG.qualityFirstLod=quality;}
    }
}
