package dev.xantha.vss.client.prediction;

import com.mojang.serialization.Lifecycle;
import dev.xantha.vss.networking.payloads.WorldgenProfileS2CPayload.DimensionProfile;
import java.util.ArrayList;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/** RTF checks object identity against the overworld entry, not merely equivalent settings. */
final class PredictionGeneratorSettings {
    private PredictionGeneratorSettings(){}
    static RegistryAccess withDecodedSettings(RegistryAccess original,DimensionProfile profile,NoiseGeneratorSettings settings){
        var id=ResourceLocation.tryParse(profile.generatorSettings());
        if(id==null||id.equals(ResourceLocation.parse("noise:inline")))return original;
        var replacement=new MappedRegistry<NoiseGeneratorSettings>(Registries.NOISE_SETTINGS,Lifecycle.stable());
        original.registry(Registries.NOISE_SETTINGS).ifPresent(registry->registry.entrySet().forEach(e->{
            if(!e.getKey().location().equals(id))replacement.register(e.getKey(),e.getValue(),RegistrationInfo.BUILT_IN);
        }));
        replacement.register(ResourceKey.create(Registries.NOISE_SETTINGS,id),settings,RegistrationInfo.BUILT_IN);
        replacement.freeze();
        var registries=new ArrayList<Registry<?>>();
        original.registries().forEach(e->{if(!e.key().equals(Registries.NOISE_SETTINGS))registries.add(e.value());});
        registries.add(replacement);
        return new RegistryAccess.ImmutableRegistryAccess(registries);
    }
}
