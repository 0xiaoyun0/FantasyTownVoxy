package me.cortex.voxy.hxz.client;

import java.nio.file.*;
import me.cortex.voxy.hxz.importer.LodImportJob;
import me.cortex.voxy.commonImpl.*;
import net.minecraft.client.Minecraft;

/** Import operations shared by the native Sodium actions; never modify the source database. */
public final class ImportActions {
    public static String importPath="",message="选择来源后，导入到当前世界和维度。";
    public static int importType;
    public static final String[] TYPES={"旧 Voxy / NeoVoxy LOD 文件夹","Minecraft / Bobby region 文件夹","Distant Horizons SQLite 文件","Minecraft region ZIP 文件"};
    public static void startImport(){
        var minecraft=Minecraft.getInstance();
        try{
            var instance=VoxyCommon.getInstance();var engine=minecraft.level==null?null:WorldIdentifier.ofEngine(minecraft.level);
            if(instance==null||engine==null)throw new IllegalStateException("请启用远景并进入目标世界");
            String[] pathParts=importPath.replaceAll("^\"|\"$","").split("!/",2);
            Path source=Path.of(pathParts[0]).toAbsolutePath().normalize();
            if(!Files.exists(source))throw new IllegalArgumentException("路径不存在");
            boolean started;
            if(importType==0)started=instance.getImportManager().makeAndRunIfNone(engine,()->new LodImportJob(source,engine));
            else if(importType==2){
                if(!me.cortex.voxy.commonImpl.importers.DHImporter.HasRequiredLibraries)throw new IllegalArgumentException("缺少 SQLite 导入组件");
                started=instance.getImportManager().makeAndRunIfNone(engine,()->new me.cortex.voxy.commonImpl.importers.DHImporter(source.toFile(),engine,minecraft.level,instance.getServiceManager(),instance.savingServiceRateLimiter));
            }else{
                String inner="";
                if(importType==3){
                    try(var zip=new java.util.zip.ZipFile(source.toFile())){
                        if(zip.size()>100000)throw new IllegalArgumentException("ZIP 文件条目过多，请先提取所需维度的 region 文件夹");
                        var folders=new java.util.TreeSet<String>();var entries=zip.entries();
                        while(entries.hasMoreElements()){
                            var entry=entries.nextElement();String name=entry.getName();String base=name.substring(name.lastIndexOf('/')+1);
                            if(!entry.isDirectory()&&base.matches("r\\.-?\\d+\\.-?\\d+\\.mca"))folders.add(name.substring(0,name.lastIndexOf('/')+1));
                        }
                        if(pathParts.length==2){inner=pathParts[1].replace('\\','/');if(!inner.isEmpty()&&!inner.endsWith("/"))inner+="/";if(!folders.contains(inner))throw new IllegalArgumentException("ZIP 中没有指定的 region 目录");}
                        else{if(folders.size()!=1)throw new IllegalArgumentException("ZIP 含多个维度或没有区块；在路径后加 !/region 或 !/DIM-1/region 指定目录。");inner=folders.first();}
                    }
                }else{
                    try(var files=Files.list(source)){if(files.noneMatch(p->p.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.mca")))throw new IllegalArgumentException("请选择包含 r.x.z.mca 文件的 region 目录");}
                }
                final String selectedInner=inner;
                started=instance.getImportManager().makeAndRunIfNone(engine,()->{
                    var importer=new me.cortex.voxy.commonImpl.importers.WorldImporter(engine,minecraft.level,instance.getServiceManager(),instance.savingServiceRateLimiter);
                    if(importType==3)importer.importZippedRegionDirectoryAsync(source.toFile(),selectedInner);else importer.importRegionDirectoryAsync(source.toFile());return importer;
                });
            }
            message=started?"导入任务已启动；可以关闭此面板继续游戏。":"已有导入任务，请等待或取消。";
        }catch(Exception e){message="无法导入："+e.getMessage();}
    }

    public static void cancel(){
        var mc=Minecraft.getInstance();var instance=VoxyCommon.getInstance();
        var engine=mc.level==null?null:WorldIdentifier.ofEngine(mc.level);
        if(instance!=null&&engine!=null)instance.getImportManager().cancelImport(engine);
        message="已请求取消；已导入部分保留。";
    }
}

