/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.Holder
 *  net.minecraft.core.RegistryAccess
 *  net.minecraft.resources.ResourceKey
 *  net.minecraft.util.profiling.ProfilerFiller
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.dimension.DimensionType
 *  net.minecraft.world.level.storage.WritableLevelData
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Unique
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package me.cortex.voxy.common.mixin;

import java.util.function.Supplier;
import me.cortex.voxy.commonImpl.IWorldGetIdentifier;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Level.class})
public class MixinLevelCommon
implements IWorldGetIdentifier {
    @Unique
    private WorldIdentifier voxy$identifier;

    @Inject(method={"<init>(Lnet/minecraft/world/level/storage/WritableLevelData;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/core/Holder;Ljava/util/function/Supplier;ZZJI)V"}, at={@At(value="RETURN")})
    private void voxy$injectIdentifier(WritableLevelData properties, ResourceKey<Level> key, RegistryAccess registryManager, Holder<DimensionType> dimensionEntry, Supplier<ProfilerFiller> profiler, boolean isClient, boolean debugWorld, long seed, int maxChainedNeighborUpdates, CallbackInfo ci) {
        this.voxy$identifier = key != null ? new WorldIdentifier(key, seed, dimensionEntry == null ? null : (ResourceKey)dimensionEntry.unwrapKey().orElse(null), isClient ? me.cortex.voxy.hxz.HxzIdentity.clientNamespace.get() : "") : null;
    }

    @Override
    public WorldIdentifier voxy$getIdentifier() {
        return this.voxy$identifier;
    }
}

