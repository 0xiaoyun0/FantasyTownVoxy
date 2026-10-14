package me.cortex.voxy.hxz.mixin;

import dev.xantha.vss.api.VSSApi;
import dev.xantha.vss.api.VoxelColumnData;
import me.cortex.voxy.hxz.HxzSettings;
import me.cortex.voxy.hxz.compat.SablePlotGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value=VSSApi.class,remap=false)
public class ColumnScopeMixin {
    @Unique private static boolean hxz$reject(ClientLevel level,ResourceKey<Level> dimension,int x,int z){
        return (HxzSettings.CONFIG.sessionGuards&&(level==null||level!=Minecraft.getInstance().level||!level.dimension().equals(dimension)))
            || SablePlotGuard.excludes(level,x,z);
    }
    @Inject(method="dispatchColumn",at=@At("HEAD"),cancellable=true)
    private static void hxz$dispatch(ClientLevel level,ResourceKey<Level> dimension,int x,int z,VoxelColumnData data,CallbackInfo ci){
        if(hxz$reject(level,dimension,x,z))ci.cancel();
    }
    @Inject(method="dispatchColumnAndReport",at=@At("HEAD"),cancellable=true)
    private static void hxz$report(ClientLevel level,ResourceKey<Level> dimension,int x,int z,VoxelColumnData data,CallbackInfoReturnable<Boolean> ci){
        if(hxz$reject(level,dimension,x,z))ci.setReturnValue(false);
    }
}
