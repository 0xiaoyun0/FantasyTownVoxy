package me.cortex.voxy.client;

import java.util.UUID;
import me.cortex.voxy.commonImpl.ImportManager;
import me.cortex.voxy.commonImpl.importers.IDataImporter;
import me.cortex.voxy.hxz.importer.LodImportJob;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;

/** Import errors/cancellation must not be announced as a successful import. */
public final class ClientImportManager extends ImportManager {
    @Override protected synchronized ImportTask createImportTask(IDataImporter importer){return new ClientTask(importer);}
    private final class ClientTask extends ImportTask {
        private final LerpingBossEvent bar=new LerpingBossEvent(UUID.randomUUID(),Component.literal("Fantasy Town Voxy 导入"),0,BossEvent.BossBarColor.GREEN,BossEvent.BossBarOverlay.PROGRESS,false,false,false);
        ClientTask(IDataImporter importer){super(importer);Minecraft.getInstance().execute(()->Minecraft.getInstance().gui.getBossOverlay().update(ClientboundBossEventPacket.createAddPacket(bar)));}
        @Override protected boolean onUpdate(int completed,int total){
            if(!super.onUpdate(completed,total))return false;
            Minecraft.getInstance().execute(()->{bar.setProgress((float)completed/Math.max(1,total));bar.setName(Component.literal("Fantasy Town Voxy 导入："+completed+" / "+total));var overlay=Minecraft.getInstance().gui.getBossOverlay();overlay.update(ClientboundBossEventPacket.createUpdateProgressPacket(bar));overlay.update(ClientboundBossEventPacket.createUpdateNamePacket(bar));});return true;
        }
        @Override protected void onCompleted(int total){
            super.onCompleted(total);
            String message=importer instanceof LodImportJob?LodImportJob.status:"Fantasy Town Voxy 导入已结束，处理 "+total+" 个区块。";
            Minecraft.getInstance().execute(()->{Minecraft.getInstance().gui.getBossOverlay().update(ClientboundBossEventPacket.createRemovePacket(bar.getId()));Minecraft.getInstance().gui.getChat().addMessage(Component.literal(message));});
        }
        @Override protected void shutdown(){super.shutdown();Minecraft.getInstance().execute(()->Minecraft.getInstance().gui.getBossOverlay().update(ClientboundBossEventPacket.createRemovePacket(bar.getId())));}
    }
}
