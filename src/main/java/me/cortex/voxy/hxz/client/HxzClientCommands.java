package me.cortex.voxy.hxz.client;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import me.cortex.voxy.hxz.HxzSettings;
public final class HxzClientCommands {
    private static int reply(CommandSourceStack source,String text){source.sendSuccess(()->Component.literal(text),false);return 1;}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        var root=Commands.literal("voxyhxz").executes(c->reply(c.getSource(),HxzClient.status()+" | /voxyhxz profile original|balanced|smooth ; /voxyhxz set <option> <value>"));
        root.then(Commands.literal("status").executes(c->reply(c.getSource(),HxzClient.status())));
        root.then(Commands.literal("menu").executes(c->{net.minecraft.client.Minecraft.getInstance().execute(()->net.minecraft.client.Minecraft.getInstance().setScreen(SodiumPanel.createScreen(null)));return 1;}));
        root.then(Commands.literal("reload").executes(c->{LiveClientConfig.reload();return reply(c.getSource(),LiveClientConfig.message);}));
        root.then(Commands.literal("importlod").then(Commands.argument("path",StringArgumentType.greedyString()).executes(c->{
            try{var mc=net.minecraft.client.Minecraft.getInstance();var engine=mc.level==null?null:me.cortex.voxy.commonImpl.WorldIdentifier.ofEngine(mc.level);var instance=me.cortex.voxy.commonImpl.VoxyCommon.getInstance();
                if(engine==null||instance==null)return reply(c.getSource(),"请先进入目标世界并启用远景。");
                var path=java.nio.file.Path.of(StringArgumentType.getString(c,"path").replaceAll("^\"|\"$",""));
                boolean started=instance.getImportManager().makeAndRunIfNone(engine,()->new me.cortex.voxy.hxz.importer.LodImportJob(path,engine));
                return reply(c.getSource(),started?"正在检查并导入到当前世界/维度；/voxy import cancel 可取消。":"已有导入任务。");
            }catch(RuntimeException e){return reply(c.getSource(),"导入失败："+e.getMessage());}
        })));
        root.then(Commands.literal("prediction").executes(c->reply(c.getSource(),"VSS prediction="+dev.xantha.vss.config.VSSClientConfig.CONFIG.enablePrediction))
            .then(Commands.argument("enabled",BoolArgumentType.bool()).executes(c->{
                boolean enabled=BoolArgumentType.getBool(c,"enabled");
                dev.xantha.vss.config.VSSClientConfig.CONFIG.enablePrediction=enabled;
                dev.xantha.vss.config.VSSClientConfig.CONFIG.save();
                return reply(c.getSource(),"VSS 地形预测="+enabled+"；已保存。服务器真实 LOD 共享独立控制。");
            })));
        var profile=Commands.literal("profile");
        for(String name:new String[]{"original","balanced","smooth"})profile.then(Commands.literal(name).executes(c->{HxzSettings.CONFIG.profile(name);LiveClientConfig.changed(true);LiveClientConfig.save();HxzClient.reset();return reply(c.getSource(),"HXZ profile="+name+"；已保存，正在热更新远景。");}));
        root.then(profile);
        var performance=Commands.literal("performance");
        for(String name:new String[]{"low","balanced","quality"})performance.then(Commands.literal(name).executes(c->{
            HxzSettings.CONFIG.performanceProfile(name);
            dev.xantha.vss.config.VSSClientConfig.CONFIG.performanceTier=name.equals("low")?"low":name.equals("quality")?"high":"medium";
            LiveClientConfig.changed(true);LiveClientConfig.save();HxzClient.reset();
            return reply(c.getSource(),"已应用性能预设 "+name+"，保留画面模式和最大视距；可在钠面板继续微调。");
        }));
        root.then(performance);
        var set=Commands.literal("set");
        for(var o:HxzSettings.OPTIONS){
            var key=Commands.literal(o.key()).executes(c->reply(c.getSource(),o.key()+"="+HxzSettings.CONFIG.get(o.key())+" | "+o.help()));
            if(HxzSettings.CONFIG.get(o.key()) instanceof Boolean)
                key.then(Commands.argument("value",BoolArgumentType.bool()).executes(c->change(c.getSource(),o,BoolArgumentType.getBool(c,"value"))));
            else key.then(Commands.argument("value",IntegerArgumentType.integer(o.min(),o.max())).executes(c->change(c.getSource(),o,IntegerArgumentType.getInteger(c,"value"))));
            set.then(key);
        }
        root.then(set);dispatcher.register(root);
    }
    private static int change(CommandSourceStack source,HxzSettings.Option o,Object value){
        HxzSettings.CONFIG.set(o.key(),value);
        if(o.key().equals("receiveServerLod"))dev.xantha.vss.config.VSSClientConfig.CONFIG.receiveServerLods=(Boolean)value;
        LiveClientConfig.changed(o.restart());LiveClientConfig.save();
        if(java.util.Set.of("originalRendering","progressiveLoading","adaptiveDistance").contains(o.key()))HxzClient.reset();
        return reply(source,o.label()+"="+value+"；已保存并安排热更新。画面模式和性能调度分别控制。");
    }
}
