package me.cortex.voxy.hxz.config;

import com.google.gson.*;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.file.*;

/** Atomic replacement and transactional reload: a bad edit never replaces live settings. */
public final class ConfigIO {
    public static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    public static void write(Path path,String json)throws IOException {
        Files.createDirectories(path.getParent());
        Path temp=Files.createTempFile(path.getParent(),path.getFileName()+".",".tmp");
        try {
            Files.writeString(temp,json);
            try {Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temp);}
    }
    public static <T>T read(Path path,Class<T> type,Gson gson)throws IOException {
        if(Files.size(path)>1_048_576)throw new IOException("配置文件超过 1 MiB");
        String text=Files.readString(path);
        JsonElement tree=JsonParser.parseString(text);
        if(!tree.isJsonObject())throw new IOException("配置必须是 JSON 对象");
        T value=gson.fromJson(tree,type);
        if(value==null)throw new IOException("配置为空");
        return value;
    }
    public static void copy(Object source,Object target) {
        if(source.getClass()!=target.getClass())throw new IllegalArgumentException("Config types differ");
        try {for(Field f:source.getClass().getFields())if(!Modifier.isStatic(f.getModifiers())&&!Modifier.isFinal(f.getModifiers()))f.set(target,f.get(source));}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
}
