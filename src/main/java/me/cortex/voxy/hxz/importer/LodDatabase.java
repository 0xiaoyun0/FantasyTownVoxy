package me.cortex.voxy.hxz.importer;

import java.nio.*;
import java.nio.file.*;
import java.util.*;
import org.rocksdb.*;

/** No create flags, no writes, no use of the live target database's palette. */
public final class LodDatabase implements AutoCloseable {
    private final List<ColumnFamilyHandle> handles=new ArrayList<>();
    private final List<ColumnFamilyOptions> familyOptions=new ArrayList<>();
    private DBOptions options;
    private RocksDB db;
    private ColumnFamilyHandle sections,mappings;
    public LodDatabase(Path source)throws Exception {this(source,null);}
    public LodDatabase(Path source,Path protectedRoot)throws Exception {
        RocksDB.loadLibrary();
        Path path=source.toRealPath();
        if(Files.isRegularFile(path)&&path.getFileName().toString().equals("CURRENT"))path=path.getParent();
        if(!Files.isRegularFile(path.resolve("CURRENT"))) {
            List<Path> found;
            try(var walk=Files.find(path,5,(p,a)->a.isRegularFile()&&p.getFileName().toString().equals("CURRENT"))){found=walk.limit(3).toList();}
            if(found.size()!=1)throw new IllegalArgumentException(found.isEmpty()?"没有找到 Voxy RocksDB 数据库；请选择含 CURRENT 的 storage 文件夹。":"文件夹包含多个世界；请选定一个世界的 storage 文件夹，避免导错维度。");
            path=found.getFirst().getParent();
        }
        if(protectedRoot!=null&&path.startsWith(protectedRoot.toRealPath()))throw new IllegalArgumentException("不能从正在使用的目标缓存导入自身；请选择旧版缓存或单独备份。");
        try {
            options=new DBOptions().setCreateIfMissing(false).setCreateMissingColumnFamilies(false).setMaxOpenFiles(32);
            List<byte[]> names;
            try(Options list=new Options()){names=RocksDB.listColumnFamilies(list,path.toString());}
            if(names.size()>16)throw new IllegalArgumentException("不是受支持的 Voxy 数据库");
            List<ColumnFamilyDescriptor> descriptors=new ArrayList<>();
            for(byte[] name:names){var opts=new ColumnFamilyOptions().setWriteBufferSize(1<<20);familyOptions.add(opts);descriptors.add(new ColumnFamilyDescriptor(name,opts));}
            db=RocksDB.openReadOnly(options,path.toString(),descriptors,handles);
            for(int i=0;i<names.size();i++){
                String name=new String(names.get(i),java.nio.charset.StandardCharsets.UTF_8);
                if(name.equals("world_sections"))sections=handles.get(i);
                if(name.equals("id_mappings"))mappings=handles.get(i);
            }
            if(sections==null||mappings==null)throw new IllegalArgumentException("缺少 Voxy 区段或方块映射表");
        }catch(Exception e){close();throw e;}
    }
    public RocksIterator sections(){return db.newIterator(sections);}
    public RocksIterator mappings(){return db.newIterator(mappings);}
    public static long key(byte[] bytes){if(bytes.length!=8)throw new IllegalArgumentException("无效的区段坐标");return ByteBuffer.wrap(bytes).getLong();}
    @Override public void close(){for(var h:handles)h.close();handles.clear();if(db!=null){db.close();db=null;}for(var o:familyOptions)o.close();familyOptions.clear();if(options!=null){options.close();options=null;}}
}
