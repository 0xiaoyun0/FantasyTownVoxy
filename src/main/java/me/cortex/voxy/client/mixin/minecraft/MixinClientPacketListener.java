/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.multiplayer.ClientPacketListener
 *  net.minecraft.network.protocol.game.ClientboundLoginPacket
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.VoxyClientInstance;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={ClientPacketListener.class})
public class MixinClientPacketListener {
    @Inject(method={"handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V"}, at={@At(value="INVOKE", target="Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;commonPlayerSpawnInfo()Lnet/minecraft/network/protocol/game/CommonPlayerSpawnInfo;")})
    private void voxy$init(ClientboundLoginPacket packet, CallbackInfo ci) {
        me.cortex.voxy.hxz.client.HxzClient.beginJoin();
        if (VoxyCommon.isAvailable() && !VoxyClientInstance.isInGame) {
            VoxyClientInstance.isInGame = true;
            if (VoxyConfig.CONFIG.enabled) {
                if (VoxyCommon.getInstance() != null) {
                    VoxyCommon.shutdownInstance();
                }
                VoxyCommon.createInstance();
            }
        }
    }
}

