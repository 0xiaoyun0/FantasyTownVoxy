package me.cortex.voxy.hxz.client;

import java.util.*;
import java.util.function.Consumer;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.caffeinemc.mods.sodium.client.gui.prompt.ScreenPrompt;
import net.caffeinemc.mods.sodium.client.gui.widgets.*;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/** Uses Sodium's modal surface and flat buttons; text input stays inside the video settings. */
public final class SodiumPrompt extends ScreenPrompt {
    private final VideoSettingsScreen parent;
    private final int boxWidth,boxHeight;
    private final String initial;
    private final Consumer<String> submit;
    private final String actionLabel;
    private List<AbstractWidget> buttons=List.of();
    private EditBox input;
    private String error="";
    private SodiumPrompt(VideoSettingsScreen parent,String title,String detail,String initial,String actionLabel,Consumer<String> submit){
        super(parent,List.<FormattedText>of(Component.literal(title),Component.literal(detail)),
            Math.min(400,parent.width-16),Math.min(220,parent.height-12),new Action(Component.empty(),()->{}));
        this.parent=parent;this.boxWidth=Math.min(400,parent.width-16);this.boxHeight=Math.min(220,parent.height-12);
        this.initial=initial;this.submit=submit;this.actionLabel=actionLabel;
    }
    public static void show(Screen parent,String title,String detail){
        open(parent,title,detail,null,null,null);
    }
    public static void confirm(Screen parent,String title,String detail,String action,Runnable submit){
        open(parent,title,detail,null,action,s->submit.run());
    }
    public static void input(Screen parent,String title,String detail,String value,Consumer<String> submit){
        open(parent,title,detail,value,"确认",submit);
    }
    private static void open(Screen screen,String title,String detail,String value,String action,Consumer<String> submit){
        if(screen instanceof VideoSettingsScreen parent){
            var prompt=new SodiumPrompt(parent,title,detail,value,action,submit);parent.setPrompt(prompt);prompt.init();
        }
    }
    @Override public void init(){
        super.init();
        int x=parent.getDimensions().getCenterX()-boxWidth/2,y=parent.getDimensions().getCenterY()-boxHeight/2;
        List<AbstractWidget> list=new ArrayList<>();
        list.add(new FlatButtonWidget(new Dim2i(x+boxWidth-84,y+boxHeight-24,80,20),Component.literal("返回"),()->parent.setPrompt(null),true,false));
        if(submit!=null)list.add(new FlatButtonWidget(new Dim2i(x+boxWidth-198,y+boxHeight-24,110,20),Component.literal(actionLabel),this::accept,true,false));
        buttons=List.copyOf(list);
        if(initial!=null){
            input=new EditBox(Minecraft.getInstance().font,x+10,y+boxHeight-74,boxWidth-20,20,Component.literal("输入值"));
            input.setMaxLength(2048);input.setValue(initial);input.setFocused(true);
        }
    }
    private void accept(){
        try{submit.accept(input==null?"":input.getValue());parent.setPrompt(null);}
        catch(RuntimeException e){error=Objects.toString(e.getMessage(),"输入无效");}
    }
    @Override public List<AbstractWidget> getWidgets(){return buttons;}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float delta){
        super.render(graphics,mouseX,mouseY,delta);graphics.pose().pushPose();graphics.pose().translate(0,0,1200);
        if(input!=null)input.render(graphics,mouseX,mouseY,delta);
        if(!error.isEmpty())graphics.drawString(Minecraft.getInstance().font,
            Minecraft.getInstance().font.plainSubstrByWidth(error,boxWidth-20),
            parent.getDimensions().getCenterX()-boxWidth/2+10,parent.getDimensions().getCenterY()+boxHeight/2-45,0xff9988);
        graphics.pose().popPose();
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(input!=null&&input.mouseClicked(x,y,button))return true;
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==256){parent.setPrompt(null);return true;}
        if((key==257||key==335)&&submit!=null){accept();return true;}
        return input!=null&&input.keyPressed(key,scan,modifiers);
    }
    @Override public boolean charTyped(char c,int modifiers){return input!=null&&input.charTyped(c,modifiers);}
}
