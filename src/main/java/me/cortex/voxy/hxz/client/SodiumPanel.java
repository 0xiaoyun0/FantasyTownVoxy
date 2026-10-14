package me.cortex.voxy.hxz.client;

import java.util.*;
import java.util.function.Predicate;
import net.caffeinemc.mods.sodium.client.config.ConfigManager;
import net.caffeinemc.mods.sodium.client.config.structure.*;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.caffeinemc.mods.sodium.client.gui.options.control.AbstractOptionList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** The only settings entry point. Sodium owns layout, scrolling, search and draft/apply behavior. */
public final class SodiumPanel {
    private static final Set<ResourceLocation> ownIds=new HashSet<>(),adminIds=new HashSet<>();
    static boolean refreshing;
    private static int modalKey=-1;
    public static void modalKeyPressed(ScreenEvent.KeyPressed.Pre event){
        if(event.getScreen() instanceof VideoSettingsScreen screen&&screen.getPrompt() instanceof SodiumPrompt prompt){
            modalKey=event.getKeyCode();prompt.keyPressed(event.getKeyCode(),event.getScanCode(),event.getModifiers());event.setCanceled(true);
        }
    }
    public static void modalKeyReleased(ScreenEvent.KeyReleased.Pre event){
        if(event.getScreen() instanceof VideoSettingsScreen&&event.getKeyCode()==modalKey){modalKey=-1;event.setCanceled(true);}
    }
    public static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("voxy",path);}
    public static ResourceLocation adminId(String key){return id("admin_"+key.toLowerCase(Locale.ROOT));}
    static void register(ResourceLocation id,boolean admin){ownIds.add(id);if(admin)adminIds.add(id);}
    public static OptionPage page(String name){
        if(ConfigManager.CONFIG!=null)for(var mod:ConfigManager.CONFIG.getModOptions())if(mod.configId().equals("voxy"))
            for(var page:mod.pages())if(page instanceof OptionPage options&&page.name().getString().equals(name))return options;
        return null;
    }
    public static Screen createScreen(Screen parent){return VideoSettingsScreen.createScreen(parent,page("基本显示"));}
    public static void tick(){
        AdminClient.tick();
        if(AdminClient.updated){
            AdminClient.updated=false;refreshBindings(adminIds::contains);
        }
    }
    /** Only the requested scope loses its draft; other mods' unapplied edits survive a server response. */
    @SuppressWarnings({"rawtypes","unchecked"})
    static void refreshBindings(Predicate<ResourceLocation> replace){
        var config=ConfigManager.CONFIG;if(config==null)return;
        Map<StatefulOption,Object> drafts=new IdentityHashMap<>();
        Set<Option> reset=Collections.newSetFromMap(new IdentityHashMap<>());
        for(var id:ownIds)if(replace.test(id)){var option=config.getOption(id);if(option!=null)reset.add(option);}
        for(var mod:config.getModOptions())for(var page:mod.pages())for(var group:page.groups())for(var option:group.options())
            if(option instanceof StatefulOption state&&state.hasChanged()&&!reset.contains(state))drafts.put(state,state.getValidatedValue());
        refreshing=true;
        try{config.resetAllOptionsFromBindings();drafts.forEach(StatefulOption::modifyValue);config.invalidateGlobalRebuildDependents();}
        finally{refreshing=false;}
    }
    static boolean adminDirty(){
        var config=ConfigManager.CONFIG;
        return config!=null&&adminIds.stream().anyMatch(id->{var option=config.getOption(id);return option!=null&&option.hasChanged();});
    }
    static boolean clientDirty(){
        var config=ConfigManager.CONFIG;
        return config!=null&&ownIds.stream().filter(id->!adminIds.contains(id)).anyMatch(id->{var option=config.getOption(id);return option!=null&&option.hasChanged();});
    }
    static void adminAction(Screen screen,String action){
        if(AdminClient.pending){SodiumPrompt.show(screen,"服务器配置",AdminClient.message);return;}
        Runnable run=()->AdminClient.request(action,Map.of());
        if(adminDirty())SodiumPrompt.confirm(screen,"放弃尚未应用的服务器修改？","重新读取将用服务器当前值替换管理员页面的草稿。客户端草稿会保留。","放弃并继续",run);
        else run.run();
    }
    static void reloadClient(Screen screen){
        Runnable run=()->{LiveClientConfig.reload();refreshBindings(id->ownIds.contains(id)&&!adminIds.contains(id));};
        if(clientDirty())SodiumPrompt.confirm(screen,"从文件重新读取客户端设置？","这会替换尚未应用的客户端草稿。服务器设置和其他模组的草稿会保留。","重新读取",run);
        else{run.run();SodiumPrompt.show(screen,"客户端热重载",LiveClientConfig.message);}
    }
    static void performancePreset(Screen screen,String preset){
        SodiumPrompt.confirm(screen,"应用性能预设？","替换本模组尚未应用的客户端草稿并保存；保留服务器设置和其他模组草稿。画面模式及最大视距保持当前已保存值。","应用预设",()->{
            me.cortex.voxy.hxz.HxzSettings.CONFIG.performanceProfile(preset);
            dev.xantha.vss.config.VSSClientConfig.CONFIG.performanceTier=preset.equals("low")?"low":preset.equals("quality")?"high":"medium";
            LiveClientConfig.changed(true);LiveClientConfig.save();
            refreshBindings(id->ownIds.contains(id)&&!adminIds.contains(id));
        });
    }
    public static void precisionInput(ScreenEvent.MouseButtonPressed.Pre event){
        if(event.getButton()!=1||!(event.getScreen() instanceof VideoSettingsScreen screen)||screen.getPrompt()!=null)return;
        var config=ConfigManager.CONFIG;if(config==null)return;
        for(var widget:screen.children())if(widget instanceof AbstractOptionList list&&list.isMouseOver(event.getMouseX(),event.getMouseY()))
            for(var control:list.getControls())if(control.isMouseOver(event.getMouseX(),event.getMouseY())&&control.getOption() instanceof IntegerOption option&&option.isEnabled()
                &&ownIds.stream().anyMatch(id->config.getOption(id)==option)){
                var range=option.getSteppedValidator();
                SodiumPrompt.input(screen,option.getName().getString(),"输入 "+range.min()+" 至 "+range.max()+" 的整数；确认后仍需点击钠的“应用”。",
                    Integer.toString(option.getValidatedValue()),value->{
                        int n;try{n=Integer.parseInt(value.strip());}catch(NumberFormatException e){throw new IllegalArgumentException("请输入整数");}
                        if(!range.isValueValid(n))throw new IllegalArgumentException("数值超出允许范围或步长不符");
                        option.modifyValue(n);
                    });
                event.setCanceled(true);return;
            }
    }
}
