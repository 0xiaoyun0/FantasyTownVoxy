import java.nio.*;
import java.nio.file.*;
import java.util.*;
import me.cortex.voxy.hxz.core.SessionNamespaces;
import me.cortex.voxy.hxz.importer.*;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import org.rocksdb.*;

/** Reopen a real LOD database through the login namespace and world-directory key. */
public class PersistenceTest {
    static WorldIdentifier join() {
        SessionNamespaces.beginConfiguration();
        return new WorldIdentifier(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse("minecraft:overworld")),
                54321L,null,SessionNamespaces.beginJoin());
    }
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]).resolve("rejoin-"+UUID.randomUUID());
        WorldIdentifier first=join();long oldEpoch=SessionNamespaces.epoch();
        Path saved=root.resolve(first.getWorldId()).resolve("storage");Files.createDirectories(saved);
        long key=0x0000001000000100L;
        byte[] raw=new byte[16+32768*2+16];var b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        b.putLong(key).putLong(2).putShort(16,(short)1).putLong(16+32768*2+8,777L<<27);
        RocksDB.loadLibrary();var handles=new ArrayList<ColumnFamilyHandle>();
        try(var options=new DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true);var family=new ColumnFamilyOptions()) {
            var descriptors=List.of(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY,family),new ColumnFamilyDescriptor("world_sections".getBytes(),family),new ColumnFamilyDescriptor("id_mappings".getBytes(),family));
            try(var db=RocksDB.open(options,saved.toString(),descriptors,handles)) {
                db.put(handles.get(1),ByteBuffer.allocate(8).putLong(key).array(),com.github.luben.zstd.Zstd.compress(raw));
                for(var handle:handles)handle.close();
            }
        }
        assertData(saved,key);
        System.out.println("PASS LOD record was saved and remains readable after closing RocksDB");
        SessionNamespaces.endSession();WorldIdentifier second=join();
        if(SessionNamespaces.epoch()==oldEpoch)throw new AssertionError("Session guards reused an old epoch");
        Path reopened=root.resolve(second.getWorldId()).resolve("storage");
        if(!saved.equals(reopened))throw new AssertionError("Rejoin lost saved LOD: login changed database from "+saved.getParent().getFileName()+" to "+reopened.getParent().getFileName());
        assertData(reopened,key);
        System.out.println("PASS reconnect without a server-side mod reopens the same persisted LOD record, with a fresh session epoch");
    }
    static void assertData(Path path,long key)throws Exception {
        try(var db=new LodDatabase(path);var it=db.sections()) {
            it.seekToFirst();if(!it.isValid()||LodRecord.decode(LodDatabase.key(it.key()),it.value()).at(0,0,0)!=(777L<<27))throw new AssertionError("Saved LOD record missing or corrupt");
        }
    }
}
