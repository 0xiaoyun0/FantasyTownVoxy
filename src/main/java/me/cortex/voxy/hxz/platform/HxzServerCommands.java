package me.cortex.voxy.hxz.platform;
import dev.xantha.vss.config.VSSServerConfig;
import dev.xantha.vss.networking.server.VSSServerNetworking;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
public final class HxzServerCommands {
    public static void register(RegisterCommandsEvent event){
        var root=Commands.literal("voxyhxzserver").requires(s->s.hasPermission(2));
        root.then(downloads());
        root.executes(c->status(c.getSource()));
        root.then(Commands.literal("reload").executes(c->{
            try{me.cortex.voxy.hxz.server.ServerPanel.reload(c.getSource().getServer());c.getSource().sendSuccess(()->Component.literal("HXZ 服务器配置已热更新。"),true);return 1;}
            catch(Exception e){c.getSource().sendFailure(Component.literal("配置重载失败，保留原设置："+e.getMessage()));return 0;}
        }));
        var sharing=Commands.literal("sharing").executes(c->status(c.getSource()));
        for(boolean enabled:new boolean[]{true,false})sharing.then(Commands.literal(enabled?"on":"off").executes(c->{
            VSSServerConfig.CONFIG.enabled=enabled;VSSServerConfig.CONFIG.save();
            VSSServerNetworking.bumpAndRefreshSessionConfigs(c.getSource().getServer());
            c.getSource().sendSuccess(()->Component.literal("HXZ 服务器共享="+enabled+"，已保存并更新在线玩家会话。"),true);return 1;
        }));
        root.then(sharing);
        var prediction=Commands.literal("prediction").executes(c->status(c.getSource()));
        for(boolean enabled:new boolean[]{true,false})prediction.then(Commands.literal(enabled?"on":"off").executes(c->{
            VSSServerConfig.CONFIG.enablePredictionSync=enabled;VSSServerConfig.CONFIG.save();
            VSSServerNetworking.bumpAndRefreshSessionConfigs(c.getSource().getServer());
            c.getSource().sendSuccess(()->Component.literal("HXZ 预测信息同步="+enabled+"，已保存并更新会话。"),true);return 1;
        }));
        var safety=Commands.literal("safety").executes(c->safetyStatus(c.getSource()));
        safety.then(Commands.literal("status").executes(c->safetyStatus(c.getSource())));
        safety.then(Commands.literal("reset").executes(c->{me.cortex.voxy.hxz.server.ServerTickGuard.reset();return safetyStatus(c.getSource());}));
        for(boolean enabled:new boolean[]{true,false})safety.then(Commands.literal(enabled?"on":"off").executes(c->{
            me.cortex.voxy.hxz.server.ServerSafetyConfig.CONFIG.set("enabled",enabled);VSSServerNetworking.applyRuntimeConfig();return safetyStatus(c.getSource());
        }));
        var set=Commands.literal("set");
        for(var option:me.cortex.voxy.hxz.server.ServerSafetyConfig.OPTIONS){
            var item=Commands.literal(option.key()).executes(c->{c.getSource().sendSuccess(()->Component.literal(option.key()+"="+me.cortex.voxy.hxz.server.ServerSafetyConfig.CONFIG.get(option.key())),false);return 1;});
            if(option.bool())item.then(Commands.argument("value",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c->setSafety(c.getSource(),option.key(),com.mojang.brigadier.arguments.BoolArgumentType.getBool(c,"value"))));
            else item.then(Commands.argument("value",com.mojang.brigadier.arguments.IntegerArgumentType.integer(option.min(),option.max())).executes(c->setSafety(c.getSource(),option.key(),com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"value"))));
            set.then(item);
        }
        safety.then(set);root.then(safety);
        root.then(prediction);root.then(Commands.literal("status").executes(c->status(c.getSource())));event.getDispatcher().register(root);
    }
    private static int safetyStatus(CommandSourceStack s){s.sendSuccess(()->Component.literal(me.cortex.voxy.hxz.server.ServerTickGuard.status()),false);return 1;}
    private static int setSafety(CommandSourceStack s,String key,Object value){
        me.cortex.voxy.hxz.server.ServerSafetyConfig.CONFIG.set(key,value);VSSServerNetworking.applyRuntimeConfig();
        s.sendSuccess(()->Component.literal("HXZ server safety "+key+"="+value+"，已保存。"),true);return 1;
    }
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> downloads(){
        var command=Commands.literal("downloads").executes(c->{c.getSource().sendSuccess(()->Component.literal(me.cortex.voxy.hxz.server.ServerDownloads.status()),false);return 1;});
        command.then(Commands.literal("off").executes(c->downloadChange(c.getSource(),java.util.Map.of("safety.downloadScheduleEnabled","false"))));
        command.then(Commands.literal("block").then(Commands.argument("HH:mm HH:mm",com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(c->{
                try{
                    String times=com.mojang.brigadier.arguments.StringArgumentType.getString(c,"HH:mm HH:mm").trim();
                    if(!times.matches("\\d{2}:\\d{2}\\s+\\d{2}:\\d{2}"))throw new IllegalArgumentException("仅支持小时和分钟");
                    var parts=times.split("\\s+");
                    var start=java.time.LocalTime.parse(parts[0]);
                    var end=java.time.LocalTime.parse(parts[1]);
                    return downloadChange(c.getSource(),java.util.Map.of("safety.downloadScheduleEnabled","true","safety.downloadBlockStartHour",""+start.getHour(),"safety.downloadBlockStartMinute",""+start.getMinute(),"safety.downloadBlockEndHour",""+end.getHour(),"safety.downloadBlockEndMinute",""+end.getMinute()));
                }catch(RuntimeException e){c.getSource().sendFailure(Component.literal("时间格式应为 HH:mm，例如 17:00 23:00"));return 0;}
            })));
        command.then(Commands.literal("timezone").then(Commands.argument("offset",com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(c->{
            try{int seconds=java.time.ZoneOffset.of(com.mojang.brigadier.arguments.StringArgumentType.getString(c,"offset").trim()).getTotalSeconds();
                if(seconds%60!=0)throw new IllegalArgumentException();return downloadChange(c.getSource(),java.util.Map.of("safety.downloadUtcOffsetMinutes",""+(seconds/60)));
            }catch(RuntimeException e){c.getSource().sendFailure(Component.literal("时区格式应为 +08:00 等 UTC 偏移，范围 -12:00 至 +14:00"));return 0;}
        })));
        command.then(Commands.literal("rate").then(Commands.argument("totalKiB",com.mojang.brigadier.arguments.IntegerArgumentType.integer(64,12207))
            .then(Commands.argument("playerKiB",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,12207)).executes(c->downloadChange(c.getSource(),java.util.Map.of(
                "vss.totalBandwidthBytesPerSecond",""+(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"totalKiB")*1024),
                "safety.downloadPlayerBytesPerSecond",""+(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"playerKiB")*1024),"safety.downloadLimitPerPlayer","true"))))));
        command.then(Commands.literal("player-limit-off").executes(c->downloadChange(c.getSource(),java.util.Map.of("safety.downloadLimitPerPlayer","false"))));
        return command;
    }
    private static int downloadChange(CommandSourceStack source,java.util.Map<String,String> values){
        try{var panel=me.cortex.voxy.hxz.server.ServerPanel.snapshot("");var changes=new com.google.gson.JsonObject();values.forEach(changes::addProperty);
            me.cortex.voxy.hxz.server.ServerPanel.apply(source.getServer(),changes,panel.get("revision").getAsLong());
            source.sendSuccess(()->Component.literal("已保存并热更新。"+me.cortex.voxy.hxz.server.ServerDownloads.status()),true);return 1;
        }catch(Exception e){source.sendFailure(Component.literal("下载设置未应用："+e.getMessage()));return 0;}
    }
    private static int status(CommandSourceStack s){s.sendSuccess(()->Component.literal("HXZ sharing="+VSSServerConfig.CONFIG.enabled+"; generation="+VSSServerConfig.CONFIG.enableChunkGeneration+"; prediction="+VSSServerConfig.CONFIG.enablePredictionSync+" | "+me.cortex.voxy.hxz.server.ServerDownloads.status()),false);return 1;}
}
