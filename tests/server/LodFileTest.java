import java.nio.*;
import java.nio.file.*;
import java.util.*;
import me.cortex.voxy.hxz.importer.*;
import org.rocksdb.*;
import com.github.luben.zstd.Zstd;

public class LodFileTest {
    public static void main(String[] args)throws Exception {
        long key=0x0000001000000100L;
        byte[] raw=new byte[16+32768*2+16];var b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);b.putLong(key).putLong(2);
        b.putShort(16,(short)1);b.putLong(16+32768*2+8,777L<<27);
        for(byte[] data:List.of(raw,Zstd.compress(raw))){var r=LodRecord.decode(key,data);if(r.at(0,0,0)!=(777L<<27)||r.at(1,0,0)!=0)throw new AssertionError("palette mismatch");}
        byte[] bad=raw.clone();ByteBuffer.wrap(bad).order(ByteOrder.LITTLE_ENDIAN).putShort(16,(short)2);
        for(byte[] data:List.of(bad,Arrays.copyOf(raw,16),new byte[]{0,0,0,0})){try{LodRecord.decode(key,data);throw new AssertionError("Corrupt LOD accepted");}catch(IllegalArgumentException expected){}}
        Random rng=new Random(4);for(int i=0;i<1000;i++){byte[] fuzz=new byte[rng.nextInt(400)];rng.nextBytes(fuzz);try{LodRecord.decode(key,fuzz);throw new AssertionError("Random LOD accepted");}catch(IllegalArgumentException expected){}}
        RocksDB.loadLibrary();Path source=Path.of(args[0]).resolve("source-"+UUID.randomUUID());Files.createDirectories(source);
        List<ColumnFamilyHandle> handles=new ArrayList<>();
        try(DBOptions opts=new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);ColumnFamilyOptions family=new ColumnFamilyOptions()){
            var descriptors=List.of(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY,family),new ColumnFamilyDescriptor("world_sections".getBytes(),family),new ColumnFamilyDescriptor("id_mappings".getBytes(),family));
            try(var db=RocksDB.open(opts,source.toString(),descriptors,handles)){
                db.put(handles.get(1),ByteBuffer.allocate(8).putLong(key).array(),Zstd.compress(raw));
                for(var h:handles)h.close();handles.clear();
            }
        }
        Map<String,String> before=hashes(source);
        try(var db=new LodDatabase(source);var it=db.sections()){it.seekToFirst();if(!it.isValid()||LodRecord.decode(LodDatabase.key(it.key()),it.value()).key()!=key)throw new AssertionError("Read-only DB import failed");}
        if(!before.equals(hashes(source)))throw new AssertionError("Read-only import modified source files");
        System.out.println("PASS legacy/shared LOD record decoding, Zstd, corrupt/truncated/1000 fuzz cases, read-only RocksDB source hashes");
    }
    static Map<String,String> hashes(Path path)throws Exception{var out=new TreeMap<String,String>();try(var files=Files.list(path)){for(var f:files.toList())if(Files.isRegularFile(f))out.put(f.getFileName().toString(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f))));}return out;}
}
