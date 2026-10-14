package me.cortex.voxy.hxz.importer;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import me.cortex.voxy.common.voxelization.*;
import me.cortex.voxy.common.world.*;
import me.cortex.voxy.common.world.other.Mapper;
import me.cortex.voxy.commonImpl.importers.IDataImporter;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.state.BlockState;

/** One read-only producer; bounded records are consumed on the client thread. */
public final class LodImportJob implements IDataImporter {
    public static volatile LodImportJob active;
    public static volatile String status="尚未导入。先进入目标世界，再选择同一世界、同一维度的数据。";
    private final WorldEngine engine;
    private final Path source;
    private final Path protectedRoot;
    private final Object targetWorld;
    private final ArrayBlockingQueue<LodRecord> ready=new ArrayBlockingQueue<>(2);
    private final Map<Integer,CompoundTag> blockTags=new HashMap<>();
    private final Map<Integer,String> biomeNames=new HashMap<>();
    private final Map<Integer,Integer> blocks=new HashMap<>(),biomes=new HashMap<>();
    private volatile boolean running,stop,produced;
    private volatile String failure;
    private volatile int total;
    private int completed,part,missingBlocks;
    private LodRecord current;
    private Thread worker;
    private ICompletionCallback done;
    private IUpdateCallback update;
    public LodImportJob(Path source,WorldEngine engine){this.source=source;this.engine=engine;
        this.protectedRoot=engine.instanceIn instanceof me.cortex.voxy.client.VoxyClientInstance instance?instance.getStorageBasePath():null;
        this.targetWorld=net.minecraft.client.Minecraft.getInstance().level;
    }
    @Override public WorldEngine getEngine(){return engine;}
    @Override public boolean isRunning(){return running;}
    @Override public void runImport(IUpdateCallback update,ICompletionCallback done){
        if(active!=null&&active.running)throw new IllegalStateException("已有导入任务");
        this.update=update;this.done=done;running=true;active=this;
        worker=Thread.ofPlatform().daemon().name("HXZ LOD import reader").start(this::read);
    }
    private void read(){
        try(LodDatabase db=new LodDatabase(source,protectedRoot)){
            status="正在检查旧 LOD 格式、方块映射和完整性…";
            try(var it=db.mappings()){
                int bytes=0;
                NbtAccounter mappingBudget=NbtAccounter.create(32L*1024*1024);
                for(it.seekToFirst();it.isValid()&&!stop;it.next()){
                    byte[] key=it.key(),value=it.value();
                    if(key.length!=4||value.length>65536||(bytes+=value.length)>16*1024*1024)throw new IOException("方块映射表损坏或超过 16 MiB 限制");
                    int encoded=ByteBuffer.wrap(key).getInt(),id=encoded&0x3fffffff,type=encoded>>>30;
                    if((type!=1&&type!=2)||id>1_048_575||type==2&&id>511)throw new IOException("方块/群系映射版本不兼容");
                    if(blockTags.size()+biomeNames.size()>=65536)throw new IOException("映射条目超过安全上限");
                    CompoundTag tag=NbtIo.readCompressed(new ByteArrayInputStream(value),mappingBudget);
                    if(type==1)blockTags.put(id,tag.getCompound("block_state"));else biomeNames.put(id,tag.getString("biome_id"));
                }
                it.status();
            }
            // Full preflight before any target write. The source remains untouched even on corruption.
            try(var it=db.sections()){
                int seen=0;
                for(it.seekToFirst();it.isValid()&&!stop;it.next()){
                    long key=LodDatabase.key(it.key());
                    LodRecord record=LodRecord.decode(key,it.value());
                    for(long value:record.palette()){
                        int block=Mapper.getBlockId(value),biome=Mapper.getBiomeId(value);
                        if(block!=0&&(!blockTags.containsKey(block)||!biomeNames.containsKey(biome)))throw new IOException("LOD 引用了缺失的方块/群系映射");
                    }
                    if(WorldEngine.getLevel(key)==0)total++;
                    if(++seen%100==0){status="正在完整性检查："+seen+" 个区段";Thread.sleep(1);}
                }
                it.status();
            }
            if(total==0&&!stop)throw new IOException("数据没有最高精度区段；无法安全重建完整 LOD 层级");
            try(var it=db.sections()){
                for(it.seekToFirst();it.isValid()&&!stop;it.next()){
                    long key=LodDatabase.key(it.key());
                    if(WorldEngine.getLevel(key)==0)ready.put(LodRecord.decode(key,it.value()));
                }
                it.status();
            }
        }catch(InterruptedException cancelled){Thread.currentThread().interrupt();}
        catch(Exception e){failure=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
        finally{produced=true;}
    }
    public static void tick(){var job=active;if(job!=null)job.consume();}
    private void consume(){
        if(!running)return;
        if(stop||!engine.isLive()||net.minecraft.client.Minecraft.getInstance().level!=targetWorld){status="离开目标世界，导入已取消；源文件未修改。";finish(false);return;}
        if(failure!=null){status="导入失败："+failure;finish(false);return;}
        if(current==null){current=ready.poll();part=0;}
        if(current==null){if(produced){status="导入完成："+completed+" 个区段；缺失模组方块 "+missingBlocks+" 种（以空气代替）。";finish(true);}return;}
        try{
            var mapper=engine.getMapper();
            // Register/remap the small palette once; numeric IDs belong to the source database.
            if(part==0){
                for(int i=0;i<current.palette().length;i++){
                    long value=current.palette()[i];int oldBlock=Mapper.getBlockId(value);
                    int block=oldBlock==0?0:blocks.computeIfAbsent(oldBlock,k->{
                        var parsed=BlockState.CODEC.parse(NbtOps.INSTANCE,blockTags.get(k)).result();
                        if(parsed.isEmpty()){missingBlocks++;return 0;}return mapper.getIdForBlockState(parsed.get());
                    });
                    int biome=block==0?0:biomes.computeIfAbsent(Mapper.getBiomeId(value),k->{
                        String name=biomeNames.get(k);int existing=mapper.getIdForBiomeString(name);if(existing>=0)return existing;
                        if(mapper.getBiomeEntries().length>=512)throw new IllegalStateException("目标库群系映射已满，请在独立世界缓存中导入");
                        return mapper.getOrRegisterBiomeFromString(name);
                    });
                    current.palette()[i]=Mapper.composeMappingId((byte)Mapper.getLightId(value),block,biome);
                }
            }
            int ox=(part&1)*16,oz=((part>>1)&1)*16,oy=((part>>2)&1)*16;
            VoxelizedSection section=VoxelizedSection.createEmpty().setPosition(WorldEngine.getX(current.key())*2+(part&1),WorldEngine.getY(current.key())*2+(part>>2),WorldEngine.getZ(current.key())*2+((part>>1)&1));
            for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                long value=current.at(x+ox,y+oy,z+oz);section.section[(y<<8)|(z<<4)|x]=value;if(!Mapper.isAir(value))section.lvl0NonAirCount++;
            }
            WorldConversionFactory.mipSection(section,mapper);WorldUpdater.insertUpdate(engine,section);
            if(++part==8){completed++;current=null;update.onUpdate(completed,total);status="正在导入："+completed+" / "+total+" 个区段（可取消，已导入部分保留）";}
        }catch(RuntimeException e){status="导入已停止："+e.getMessage()+"；已导入部分保留。";finish(false);}
    }
    private void finish(boolean success){if(!running)return;running=false;stop=true;if(worker!=null)worker.interrupt();ready.clear();current=null;active=null;done.onCompletion(success?completed:0);}
    @Override public void shutdown(){status="导入已取消；已导入的数据保留，源文件未改动。";finish(false);}
}
