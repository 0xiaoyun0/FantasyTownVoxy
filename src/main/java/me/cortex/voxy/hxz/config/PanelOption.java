package me.cortex.voxy.hxz.config;

import java.util.*;
import java.util.function.*;

/** Client setting schema used by Sodium and the server's authoritative panel snapshots. */
public record PanelOption(String id,String page,String label,String help,int min,int max,
                          List<String> choices,List<String> choiceLabels,Object defaultValue,
                          Supplier<Object> read,Consumer<Object> write) {
    public Object parse(String text){
        Object sample=defaultValue;
        if(!choices.isEmpty()){if(!choices.contains(text))throw new IllegalArgumentException("请选择有效选项");return text;}
        if(sample instanceof Boolean){if(!text.equals("true")&&!text.equals("false"))throw new IllegalArgumentException("应为 true 或 false");return Boolean.parseBoolean(text);}
        if(sample instanceof Number){int n=Integer.parseInt(text);if(n<min||n>max)throw new IllegalArgumentException("范围："+min+" 至 "+max);return n;}
        if(text.length()>128)throw new IllegalArgumentException("文本过长");return text;
    }
    public String format(Object value){int i=choices.indexOf(String.valueOf(value));return i>=0?choiceLabels.get(i):value instanceof Boolean?(Boolean.TRUE.equals(value)?"开启":"关闭"):String.valueOf(value);}
    public void setText(String text){write.accept(parse(text));}
}
