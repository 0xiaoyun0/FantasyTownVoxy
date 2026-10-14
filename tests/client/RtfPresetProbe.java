package hxz.smoke;
import com.google.gson.*;
import com.mojang.serialization.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.resources.*;
import dev.xantha.vss.common.worldgen.*;

/** Runs against the user's real RTF jar inside NeoForge, not a stand-in codec. */
public final class RtfPresetProbe {
    private static String changed;
    private static boolean customize(JsonElement json){
        if(json.isJsonObject())for(var e:json.getAsJsonObject().entrySet()){
            if(e.getKey().replace("_","").equalsIgnoreCase("seaLevel")&&e.getValue().isJsonPrimitive()&&e.getValue().getAsJsonPrimitive().isNumber()){
                int old=e.getValue().getAsInt();json.getAsJsonObject().addProperty(e.getKey(),old+3);changed=e.getKey()+":"+old+"->"+(old+3);return true;
            }
            if(customize(e.getValue()))return true;
        }
        if(json.isJsonArray())for(var e:json.getAsJsonArray())if(customize(e))return true;
        return false;
    }
    @SuppressWarnings("unchecked")
    public static void run() throws Exception {
        String prefix="raccoonman.reterraforged.";
        Object defaults=Class.forName(prefix+"data.worldgen.preset.settings.Presets").getMethod("makeRTFDefault").invoke(null);
        Codec<Object> codec=(Codec<Object>)Class.forName(prefix+"data.worldgen.preset.settings.Preset").getField("DIRECT_CODEC").get(null);
        JsonElement custom=codec.encodeStart(JsonOps.INSTANCE,defaults).getOrThrow().deepCopy();
        if(!customize(custom))throw new AssertionError("No sea-level parameter in actual RTF preset");
        Object preset=codec.parse(JsonOps.INSTANCE,custom).getOrThrow();
        var key=ResourceKey.<Object>createRegistryKey(ResourceLocation.parse("reterraforged:worldgen/preset"));
        var id=ResourceKey.create(key,ResourceLocation.parse("reterraforged:preset"));
        var registry=new MappedRegistry<Object>(key,Lifecycle.stable());registry.register(id,preset,RegistrationInfo.BUILT_IN);registry.freeze();
        var server=new RegistryAccess.ImmutableRegistryAccess(List.of(registry));
        if(!FreeTerraForgedVariant.LEGACY.hasPreset(server))throw new AssertionError("Custom RTF preset was not detected");
        List<RegistryDataLoader.RegistryData<?>> codecs=List.of(new RegistryDataLoader.RegistryData<>(key,codec,false));
        var dependencies=new WorldgenRegistryDependencies(server,codecs);dependencies.require(key.location());
        JsonObject root=new JsonObject();root.add("noises",new JsonObject());root.add("density_functions",new JsonObject());root.add("custom_registries",dependencies.encode());
        Class<?> bridge=Class.forName("dev.xantha.vss.client.prediction.ClientWorldgenRegistries");
        var decode=bridge.getDeclaredMethod("decode",JsonObject.class,RegistryAccess.class,List.class);decode.setAccessible(true);
        Object decoded=decode.invoke(null,root,RegistryAccess.EMPTY,codecs);
        var access=bridge.getDeclaredMethod("access");access.setAccessible(true);
        Object received=((RegistryAccess)access.invoke(decoded)).registryOrThrow(key).get(id);
        if(!codec.encodeStart(JsonOps.INSTANCE,preset).getOrThrow().equals(codec.encodeStart(JsonOps.INSTANCE,received).getOrThrow()))throw new AssertionError("RTF custom settings changed during VSS snapshot decode");
        Class<?> api=Class.forName("dev.xantha.vss.client.prediction.FreeTerraForgedCompat$Api");
        var constructor=api.getDeclaredConstructor(ClassLoader.class,FreeTerraForgedVariant.class);constructor.setAccessible(true);
        constructor.newInstance(RtfPresetProbe.class.getClassLoader(),FreeTerraForgedVariant.LEGACY);
        OptimizationProbe.predictionCache(codec.encodeStart(JsonOps.INSTANCE,defaults).getOrThrow().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),custom.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println("HXZ_SMOKE PASS actual RTF custom preset snapshot round-trip, "+changed+", legacy runtime API available");
    }
}
