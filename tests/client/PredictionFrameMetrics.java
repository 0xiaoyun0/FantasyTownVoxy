package hxz.smoke;

import java.util.Arrays;
import java.util.Locale;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Tests only: bounded frame-interval sampling, no renderer settings or FPS-limit changes. */
public final class PredictionFrameMetrics {
    private static final long[] intervals = new long[262_144];
    private static boolean registered, active;
    private static int count, dropped;
    private static long previous;

    public static void begin() {
        if (!registered) {
            NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Post.class, PredictionFrameMetrics::frame);
            registered = true;
        }
        count = dropped = 0; previous = 0; active = true;
    }

    private static void frame(RenderFrameEvent.Post event) {
        if (!active) return;
        long now = System.nanoTime();
        if (previous != 0 && now > previous) {
            if (count < intervals.length) intervals[count++] = now - previous;
            else dropped++;
        }
        previous = now;
    }

    public static String finish() {
        active = false;
        long[] sorted = Arrays.copyOf(intervals, count);
        Arrays.sort(sorted);
        return String.format(Locale.ROOT,
                "frameSamples=%d frameDropped=%d frameP50Ms=%.3f frameP95Ms=%.3f frameP99Ms=%.3f",
                count, dropped, quantile(sorted, .50), quantile(sorted, .95), quantile(sorted, .99));
    }

    private static double quantile(long[] sorted, double proportion) {
        if (sorted.length == 0) return 0;
        return sorted[Math.max(0, (int) Math.ceil(sorted.length * proportion) - 1)] / 1_000_000.0;
    }
}
