package me.cortex.voxy.hxz.mixin;

import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import me.cortex.voxy.hxz.compat.SablePlotGuard;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=VoxelIngestService.class,remap=false)
public class SableIngestMixin {
    @Inject(method="tryIngestChunk",at=@At("HEAD"),cancellable=true)
    private static void hxz$beforeWorld(WorldIdentifier world,LevelChunk chunk,CallbackInfoReturnable<Boolean> ci){
        if(SablePlotGuard.excludes(chunk.getLevel(),chunk.getPos().x,chunk.getPos().z))ci.setReturnValue(false);
    }
    @Inject(method="enqueueIngest",at=@At("HEAD"),cancellable=true)
    private void hxz$beforeCopy(WorldEngine world,LevelChunk chunk,CallbackInfoReturnable<Boolean> ci){
        if(SablePlotGuard.excludes(chunk.getLevel(),chunk.getPos().x,chunk.getPos().z))ci.setReturnValue(false);
    }
}
