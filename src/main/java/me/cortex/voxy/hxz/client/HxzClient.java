package me.cortex.voxy.hxz.client;
import me.cortex.voxy.hxz.*;
import me.cortex.voxy.hxz.core.*;
import me.cortex.voxy.client.config.VoxyConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryUtil;

public final class HxzClient {
    private static String namespace=SessionNamespaces.ENDPOINT_NAMESPACE;
    private static volatile FrameGovernor governor=new FrameGovernor();
    private static WorkBudget meshes=new WorkBudget(),columns=new WorkBudget(),background=new WorkBudget();
    private static volatile long lastFrame;
    public static void initialize(){HxzIdentity.clientNamespace=()->{
        var s=HxzSettings.CONFIG;
        if(!s.worldIsolation||Minecraft.getInstance().getSingleplayerServer()!=null)return "";
        return s.backendOverride.isBlank()?namespace:"manual:"+s.backendOverride;
    };}
    public static void beginJoin(){namespace=SessionNamespaces.beginJoin();reset();}
    public static void reset(){governor=new FrameGovernor();meshes=new WorkBudget();columns=new WorkBudget();background=new WorkBudget();lastFrame=0;}
    public static void frame(int pending){var s=HxzSettings.CONFIG;var r=Runtime.getRuntime();lastFrame=System.nanoTime();
        governor.update(lastFrame,VoxyConfig.CONFIG.sectionRenderDistance,s.minimumFps,(double)(r.totalMemory()-r.freeMemory())/r.maxMemory(),pending,s.warmupSeconds,s.growthBlocksPerSecond/512.0,s.adaptiveDistance,s.progressiveLoading);}
    public static float effectiveDistance(){var s=HxzSettings.CONFIG;return s.scheduling()&&(s.adaptiveDistance||s.progressiveLoading)?(float)governor.distance():VoxyConfig.CONFIG.sectionRenderDistance;}
    public static int initialDistance(int original){var s=HxzSettings.CONFIG;return s.scheduling()&&s.progressiveLoading?1:original;}
    public static int columnUpdates(int original){var s=HxzSettings.CONFIG;return s.scheduling()&&s.frameBudget?columns.take(System.nanoTime(),governor.columnsPerSecond(s.rootUpdatesPerSecond),2):original;}
    public static boolean deferPrediction(){var s=HxzSettings.CONFIG;return s.scheduling()&&s.sharedPredictionBudget&&lastFrame!=0&&System.nanoTime()-lastFrame<1_000_000_000L&&governor.underPressure();}
    public static int capRange(int requested,int unitBlocks,int minimumBlocks){var s=HxzSettings.CONFIG;
        if(!s.scheduling()||(!s.adaptiveDistance&&!s.progressiveLoading)||!VoxyConfig.CONFIG.isRenderingEnabled()||requested<=0||lastFrame!=0&&System.nanoTime()-lastFrame>5_000_000_000L)return requested;
        return Math.min(requested,Math.max(1,Math.max(minimumBlocks,(int)(effectiveDistance()*512/256)*256)/unitBlocks));}
    public static int flags(){var s=HxzSettings.CONFIG;if(!s.optimized())return s.scheduling()&&s.frameBudget&&s.strictRequestLimit?16:0;
        return 1|(s.limitCoarseMeshes?2:0)|(s.cameraPlaneFix?4:0)|(s.safeMeshGuards?8:0)|(s.strictRequestLimit?16:0)|(!s.frustumCulling?32:0)|(!s.occlusionCulling?64:0);}
    public static void uniform(long ptr){var s=HxzSettings.CONFIG;int capacity=MemoryUtil.memGetInt(ptr+200);
        boolean limited=s.scheduling()&&s.frameBudget;
        int foreground=limited?meshes.take(System.nanoTime(),governor.meshRequestsPerSecond(s.meshRequestsPerSecond),8):capacity;
        int bg=s.optimized()&&s.backgroundDetail>0&&!governor.underPressure()?background.take(System.nanoTime(),s.backgroundRequestsPerSecond,2):0;
        var requests=WorkBudget.allocate(capacity,foreground,bg,limited);
        MemoryUtil.memPutInt(ptr+200,requests.total());MemoryUtil.memPutInt(ptr+204,flags());
        MemoryUtil.memPutFloat(ptr+208,s.coarseScreenPercent/100f);
        float dist=effectiveDistance()*512;
        MemoryUtil.memPutFloat(ptr+212,s.scheduling()&&(s.adaptiveDistance||s.progressiveLoading)?dist*dist:-1);
        MemoryUtil.memPutInt(ptr+216,requests.background());
        MemoryUtil.memPutInt(ptr+220,s.optimized()?s.backgroundDetail:0);
    }
    public static String status(){return String.format(java.util.Locale.ROOT,"画面：%s；性能调度：%s；近期 %.0f FPS；远景 %.0f / %d 方块；负载：%s",HxzSettings.CONFIG.originalRendering?"NeoVoxy":"HXZ",HxzSettings.CONFIG.scheduling()?"开启":"关闭",governor.averageFps(),effectiveDistance()*512,VoxyConfig.CONFIG.sectionRenderDistance*512,governor.underPressure()?"正在减负":"正常");}
}
