package hxz.safetyprobe.mixin;
import hxz.safetyprobe.SafetyProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="dev.xantha.vss.networking.server.request.ColumnRequestBatchHandler",remap=false)
public abstract class ColumnProbeMixin {
    @Inject(method="handleColumnRequest",at=@At("HEAD"))
    private void count(net.minecraft.server.level.ServerPlayer player,
        dev.xantha.vss.networking.server.state.PlayerRequestState state,
        net.minecraft.server.level.ServerLevel level,
        dev.xantha.vss.networking.payloads.BatchChunkRequestC2SPayload payload,
        dev.xantha.vss.networking.server.request.BatchResponseAccumulator responses,int index,CallbackInfo ci){
        SafetyProbe.column(payload.requestIds()[index]);
    }
}
