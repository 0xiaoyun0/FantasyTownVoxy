package me.cortex.voxy.hxz.client;

import java.util.*;
import java.util.function.Consumer;
import net.caffeinemc.mods.sodium.api.config.ConfigState;
import net.caffeinemc.mods.sodium.api.config.StorageEventHandler;
import net.caffeinemc.mods.sodium.api.config.structure.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import me.cortex.voxy.hxz.server.ServerPanel;
import me.cortex.voxy.hxz.importer.LodImportJob;

public final class HxzOptions {
    private static final StorageEventHandler CLIENT_SAVE=LiveClientConfig::save,SERVER_SAVE=AdminClient::flush;
    private static final ResourceLocation MODE=SodiumPanel.id("hxz_originalrendering");
    private static Component text(String s){return Component.literal(s);}
    private static void button(ConfigBuilder b,OptionGroupBuilder g,String id,String label,String help,Consumer<Screen> action){
        g.addOption(b.createExternalButtonOption(SodiumPanel.id(id)).setName(text(label)).setTooltip(text(help)).setScreenConsumer(action));
    }
    public static void add(ConfigBuilder builder,ModOptionsBuilder mod){
        Map<String,OptionPageBuilder> pages=new LinkedHashMap<>();
        for(String name:List.of("基本显示","性能与加载","HXZ 画面优化","服务器共享","地形预测","预测性能与画面","数据与兼容"))
            pages.put(name,builder.createOptionPage().setName(text(name)));
        var presets=builder.createOptionGroup();
        for(String key:List.of("low","balanced","quality")){
            String label=key.equals("low")?"低端：优先流畅":key.equals("quality")?"高端：加快精修":"均衡：渐进加载";
            button(builder,presets,"performance_"+key,label,"套用线程、内存、加载速度和预测计算强度；保留画面模式、世界身份及用户最大视距。确认后直接保存。",screen->SodiumPanel.performancePreset(screen,key));
        }
        pages.get("性能与加载").addOptionGroup(presets);
        for(var o:ClientOptions.all()){
            var id=SodiumPanel.id(o.id());var group=builder.createOptionGroup();
            if(o.defaultValue() instanceof String&&o.choices().isEmpty()){
                button(builder,group,o.id(),o.label(),o.help()+" 点击后输入，确认即保存。",screen->
                    SodiumPrompt.input(screen,o.label(),o.help(),o.read().get().toString(),v->{o.setText(v);LiveClientConfig.save();}));
            }else{
                SodiumPanel.register(id,false);
                StatefulOptionBuilder<?> option;
                String help=o.help()+((o.defaultValue() instanceof Number)?" 右键本项可精确输入。":"");
                if(!o.choices().isEmpty()){
                    option=builder.createIntegerOption(id).setName(text(o.label())).setTooltip(text(help))
                        .setRange(0,o.choices().size()-1,1).setDefaultValue(o.choices().indexOf(o.defaultValue()))
                        .setValueFormatter(v->text(o.choiceLabels().get(v)))
                        .setBinding(v->o.write().accept(o.choices().get(v)),()->Math.max(0,o.choices().indexOf(o.read().get())));
                }else if(o.defaultValue() instanceof Boolean b){
                    option=builder.createBooleanOption(id).setName(text(o.label())).setTooltip(text(help))
                        .setDefaultValue(b).setBinding(o.write()::accept,()->(Boolean)o.read().get());
                }else{
                    option=builder.createIntegerOption(id).setName(text(o.label())).setTooltip(text(help))
                        .setRange(o.min(),o.max(),1).setDefaultValue(((Number)o.defaultValue()).intValue()).setValueFormatter(v->text(Integer.toString(v)))
                        .setBinding(o.write()::accept,()->((Number)o.read().get()).intValue());
                }
                option.setStorageHandler(CLIENT_SAVE);
                if(o.page().startsWith("HXZ"))option.setEnabledProvider(state->state.readIntOption(MODE)==1,MODE);
                group.addOption(option);
            }
            pages.get(o.page()).addOptionGroup(group);
        }
        pages.values().forEach(mod::addPage);
        addImport(builder,mod);addMaintenance(builder,mod);addAdmin(builder,mod);
    }
    private static void addImport(ConfigBuilder b,ModOptionsBuilder mod){
        var page=b.createOptionPage().setName(text("导入 LOD"));var sources=b.createOptionGroup();
        for(int i=0;i<ImportActions.TYPES.length;i++){
            final int type=i;
            button(b,sources,"import_source_"+i,ImportActions.TYPES[i],"选择来源后还需确认导入；不会自动写入。旧 Voxy / NeoVoxy 请选择含 CURRENT 的 storage 文件夹。",screen->{
                String selected=type<2?org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_selectFolderDialog("选择要导入的文件夹",ImportActions.importPath)
                    :org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("选择 LOD 或区块文件",ImportActions.importPath,null,null,false);
                if(selected!=null){ImportActions.importType=type;ImportActions.importPath=selected;
                    SodiumPrompt.show(screen,"已选择来源",ImportActions.TYPES[type]+"\n"+selected+"\n点击“开始导入到当前世界”继续。");}
            });
        }
        button(b,sources,"import_path","查看或粘贴源路径","保留当前选择的文件类型。ZIP 多维度可在路径后加 !/region 或 !/DIM-1/region。",screen->
            SodiumPrompt.input(screen,ImportActions.TYPES[ImportActions.importType],"源路径；多维度 ZIP 可加 !/内部目录。",ImportActions.importPath,v->ImportActions.importPath=v.strip()));
        page.addOptionGroup(sources);var actions=b.createOptionGroup();
        button(b,actions,"import_start","开始导入到当前世界","选择来源后才会开始；需进入目标世界并启用远景。相同坐标的数据会被替换，建议先备份目标 LOD。",screen->{
            var mc=Minecraft.getInstance();
            if(mc.level==null){SodiumPrompt.show(screen,"无法导入","请先进入要接收数据的目标世界。");return;}
            if(ImportActions.importPath.isBlank()){SodiumPrompt.show(screen,"无法导入","请先选择或粘贴源路径。");return;}
            SodiumPrompt.confirm(screen,"确认导入到 "+mc.level.dimension().location(),
                ImportActions.TYPES[ImportActions.importType]+"\n"+ImportActions.importPath+"\n请确认属于当前世界和维度；同坐标远景会替换，源文件不修改。","开始导入",ImportActions::startImport);
        });
        button(b,actions,"import_status","查看导入进度与结果","导入可在后台运行，关闭设置页不影响任务。失败原因也在这里查看。",screen->
            SodiumPrompt.show(screen,"导入状态",ImportActions.message+"\n"+LodImportJob.status));
        button(b,actions,"import_cancel","取消正在运行的导入","取消后保留已导入部分；源文件不受影响。",screen->{ImportActions.cancel();SodiumPrompt.show(screen,"取消导入",ImportActions.message);});
        page.addOptionGroup(actions);mod.addPage(page);
    }
    private static void addMaintenance(ConfigBuilder b,ModOptionsBuilder mod){
        var page=b.createOptionPage().setName(text("状态与热更新"));var group=b.createOptionGroup();
        button(b,group,"client_status","查看客户端运行状态","查看当前渲染状态与最近一次设置应用结果。",screen->SodiumPrompt.show(screen,"Fantasy Town Voxy",HxzClient.status()+"\n"+LiveClientConfig.message));
        button(b,group,"client_reload","从文件热重载客户端配置","普通设置使用钠的“应用”即可。此操作用于读取外部修改的配置，格式错误时保留原配置。",SodiumPanel::reloadClient);
        button(b,group,"server_resync","重新同步服务器 LOD","接收远景已开启但未更新时使用。仍受服务器的请求限额约束。",screen->{
            dev.xantha.vss.networking.client.VSSClientNetworking.forceLodResync("Fantasy Town Voxy panel");
            SodiumPrompt.show(screen,"重新同步","已请求重新同步；服务器未安装共享组件或关闭共享时，不会收到数据。");
        });
        button(b,group,"xaero_reload","重载 Xaero 地图缓存","用于刷新已有 Xaero 地图记录。未启用地图联动时不会产生远景。",screen->{
            int n=dev.xantha.vss.networking.client.VSSClientNetworking.reloadXaeroMapData();
            SodiumPrompt.show(screen,"地图缓存","已重载地图缓存："+n);
        });
        page.addOptionGroup(group);mod.addPage(page);
    }
    private static void addAdmin(ConfigBuilder b,ModOptionsBuilder mod){
        Map<String,OptionPageBuilder> pages=new LinkedHashMap<>();
        for(String name:List.of("共享与地形","负载保护","下载限制","传输与缓存","高级参数")){
            var page=b.createOptionPage().setName(text("管理员 · "+name));var actions=b.createOptionGroup();
            String suffix=Integer.toString(pages.size());
            button(b,actions,"admin_read_"+suffix,"读取 / 刷新服务器配置","需要服务器安装本版及 OP 2 级权限。未读取时下方项不可用；修改后点击钠的“应用”。",screen->SodiumPanel.adminAction(screen,"get"));
            button(b,actions,"admin_status_"+suffix,"查看服务器状态与操作结果","服务器确认后才算应用成功。服务器项的重置操作恢复到最近读取值，不会恢复出厂配置。",screen->SodiumPrompt.show(screen,"服务器状态",AdminClient.message+"\n"+AdminClient.status));
            if(pages.isEmpty()){
                button(b,actions,"admin_reload","从文件热重载服务器配置","重新读取服务器配置文件；世界身份变更仍需重启，避免混用缓存。",screen->SodiumPanel.adminAction(screen,"reload"));
                button(b,actions,"admin_reset","恢复因异常暂停的 LOD 服务","仅恢复共享服务的熔断状态，不会恢复出厂配置。确认异常原因已排除后使用。",screen->SodiumPanel.adminAction(screen,"reset"));
            }
            page.addOptionGroup(actions);pages.put(name,page);
        }
        for(var e:ServerPanel.entries()){
            var id=SodiumPanel.adminId(e.key());SodiumPanel.register(id,true);
            Object fallback=e.bool()?Boolean.FALSE:Integer.valueOf(e.min());
            var group=b.createOptionGroup();StatefulOptionBuilder<?> option;
            String help=e.help()+" 需先读取服务器配置；修改后点击钠的“应用”。"+(e.bool()?"":" 右键可精确输入数值。");
            if(e.bool())option=b.createBooleanOption(id).setName(text(e.label())).setTooltip(text(help))
                .setDefaultProvider(state->(Boolean)AdminClient.value(e.key(),fallback),ConfigState.UPDATE_ON_REBUILD)
                .setBinding(v->AdminClient.stage(e.key(),v),()->(Boolean)AdminClient.value(e.key(),fallback));
            else option=b.createIntegerOption(id).setName(text(e.label())).setTooltip(text(help))
                .setRange(e.min(),e.max(),1).setValueFormatter(v->text(Integer.toString(v)))
                .setDefaultProvider(state->((Number)AdminClient.value(e.key(),fallback)).intValue(),ConfigState.UPDATE_ON_REBUILD)
                .setBinding(v->AdminClient.stage(e.key(),v),()->((Number)AdminClient.value(e.key(),fallback)).intValue());
            option.setEnabledProvider(state->AdminClient.editable(e.key()),ConfigState.UPDATE_ON_REBUILD);
            option.setControlHiddenWhenDisabled(true).setStorageHandler(SERVER_SAVE);
            group.addOption(option);pages.get(ServerPanel.group(e.key())).addOptionGroup(group);
        }
        pages.values().forEach(mod::addPage);
    }
}
