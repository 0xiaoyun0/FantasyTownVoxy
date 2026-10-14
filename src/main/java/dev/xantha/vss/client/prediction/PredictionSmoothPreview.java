package dev.xantha.vss.client.prediction;

import java.util.Arrays;
import java.util.function.BooleanSupplier;

/** Coarse height-field mesh: keep sampled heights without turning each sample into a giant cube. */
public final class PredictionSmoothPreview {
    private PredictionSmoothPreview() { }

    public static PredictionMesh build(ClientColumnSample[] samples,int[] colors,int[] waterTints,
            int fluidColor,int step,int grid,Object vegetation,Object simple,Object cities,BooleanSupplier current) {
        // Fine block faces, full 3-D interiors, void edges and structures keep VSS topology.
        if(step<16||grid<3||grid>66||samples==null||samples.length!=grid*grid||cities!=null
                ||vegetation instanceof PredictionVegetation.Tile plants&&!plants.blocks().isEmpty()
                ||simple instanceof PredictionSimpleVegetation.Result result&&!result.forms().isEmpty())return null;
        // One captured cave must not turn a kilometre-wide exterior preview
        // back into giant pillars. Its top remains a valid height-field point;
        // underground bands are restored by the ordinary fine mesher.
        for(var sample:samples)if(sample==null||!sample.hasSurface()
                ||sample.volume()!=null||sample.cityGround())return null;
        int margin=grid%2==0?1:0,axis=grid-1-margin,cells=axis*axis,vertices=cells*6;
        float[] positions=new float[vertices*3],normals=new float[vertices*3];
        float[] water=new float[vertices*3],waterNormals=new float[vertices*3];
        int[] material=new int[vertices],waterColors=new int[vertices],waterOffsets=new int[cells],waterCounts=new int[cells];
        boolean[] wet=new boolean[cells];int waterVertices=0;
        int[] cornerOrder={0,1,2,0,2,3};
        for(int z=0;z<axis;z++){
            if(Thread.currentThread().isInterrupted()||!current.getAsBoolean())throw new java.util.concurrent.CancellationException();
            for(int x=0;x<axis;x++){
                int cell=z*axis+x,i=(z+margin)*grid+x+margin;
                int[] indices={i,i+1,i+grid+1,i+grid};
                int block=PredictionMaterialPalette.surfaceBlock(samples[i]);
                int sprite=block==ClientColumnSample.NO_BLOCK?VssLodSpriteTable.FLAT:VssLodSpriteTable.indexForBlock(block);
                for(int v=0;v<6;v++){
                    int corner=cornerOrder[v],out=cell*6+v,source=indices[corner];
                    positions[out*3]=(x+(corner==1||corner==2?1:0))*step;
                    positions[out*3+1]=samples[source].surfaceY();
                    positions[out*3+2]=(z+(corner>=2?1:0))*step;
                    // Packed VSS stores a face group rather than a smooth normal.
                    normals[out*3+1]=1;
                    int color=colors==null?0xff527440:colors[source];
                    material[out]=sprite==VssLodSpriteTable.FLAT?color:(color&0xffffff)|((sprite&255)<<24);
                }
                waterOffsets[cell]=waterVertices;
                ClientColumnSample fluid=null;int tint=fluidColor;boolean compatible=true;
                for(int source:indices)if(samples[source].hasFluid()){
                    var next=samples[source];
                    if(fluid!=null&&(fluid.fluid()!=next.fluid()||fluid.fluidY()!=next.fluidY()||fluid.ice()!=next.ice()))compatible=false;
                    fluid=next;if(waterTints!=null&&waterTints[source]!=0)tint=waterTints[source];
                }
                // Same-height water crosses a sloped bank; the ground depth clips its dry part.
                // Different levels/kinds require the original waterfall/fluid-bank topology.
                if(!compatible)return null;
                if(fluid!=null){
                    int kind=fluid.ice()?3:fluid.fluid()==2?2:1;
                    float y=kind==3?fluid.fluidY():fluid.fluidY()-PredictionMeshBuilder.FLUID_SURFACE_DROP;
                    if(kind==2)tint=0xffff6600;
                    if(kind==3)tint=0xffa5c8ef;
                    for(int v=0;v<6;v++){
                        int out=waterVertices++,corner=cornerOrder[v];
                        water[out*3]=(x+(corner==1||corner==2?1:0))*step;water[out*3+1]=y;
                        water[out*3+2]=(z+(corner>=2?1:0))*step;
                        waterNormals[out*3+1]=kind;waterColors[out]=tint;
                    }
                    wet[cell]=true;waterCounts[cell]=6;
                }
            }
        }
        return new PredictionMesh(positions,normals,material,Arrays.copyOf(water,waterVertices*3),
                Arrays.copyOf(waterNormals,waterVertices*3),Arrays.copyOf(waterColors,waterVertices),wet,
                vertices,waterVertices,cells,null,null,waterOffsets,waterCounts,axis,step).compactForRendering();
    }
}
