package dev.g85perf.mixin;

import dev.g85perf.G85PerfMod;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ThermalThrottleMixin — Fixed for MC 1.21
 * render() signature: (RenderTickCounter, boolean) in 1.21
 */
@Mixin(GameRenderer.class)
public class ThermalThrottleMixin {

    @Unique private static final int SAMPLE_SIZE = 30;
    @Unique private static final long[] frameTimes = new long[SAMPLE_SIZE];
    @Unique private static int sampleIndex = 0;
    @Unique private static long lastFrameTime = 0;

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderEnd(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        if (!G85PerfMod.thermalProtection) return;

        long now = System.nanoTime();
        if (lastFrameTime != 0) {
            long frameTime = now - lastFrameTime;
            frameTimes[sampleIndex % SAMPLE_SIZE] = frameTime;
            sampleIndex++;

            if (sampleIndex % SAMPLE_SIZE == 0) {
                analyzeFrameTimes();
            }
        }
        lastFrameTime = now;
    }

    @Unique
    private static void analyzeFrameTimes() {
        long sum = 0;
        long max = 0;
        for (long t : frameTimes) {
            sum += t;
            if (t > max) max = t;
        }
        long avg = sum / SAMPLE_SIZE;
        long avgMs = avg / 1_000_000;
        long maxMs = max / 1_000_000;

        boolean spiking = maxMs > avgMs * 3 && avgMs > 50;

        if (spiking && !G85PerfMod.thermalThrottleActive) {
            G85PerfMod.thermalThrottleActive = true;
            G85PerfMod.LOGGER.warn("[G85Perf] Frame spike detected: avg={}ms max={}ms → thermal mode ON", avgMs, maxMs);
        } else if (!spiking && G85PerfMod.thermalThrottleActive && avgMs < 40) {
            G85PerfMod.thermalThrottleActive = false;
            G85PerfMod.LOGGER.info("[G85Perf] Stable frames: avg={}ms → thermal mode OFF", avgMs);
        }
    }
}

/**
 * ThermalThrottleMixin
 *
 * Monitors frame time variance to detect thermal throttling.
 *
 * When Helio G85 overheats:
 * - CPU frequency drops from 2.0GHz → 1.0GHz or lower
 * - Frame times become inconsistent (spike every few frames)
 * - FPS drops from 30 → 10-15 randomly
 *
 * We detect this by measuring rolling average frame time.
 * If average frame time suddenly doubles, we flag thermal throttle.
 */
@Mixin(GameRenderer.class)
public class ThermalThrottleMixin {

    @Unique private static final int SAMPLE_SIZE = 30;
    @Unique private static final long[] frameTimes = new long[SAMPLE_SIZE];
    @Unique private static int sampleIndex = 0;
    @Unique private static long lastFrameTime = 0;

    @Inject(method = "render", at = @At("TAIL"))
    private void onRenderEnd(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        if (!G85PerfMod.thermalProtection) return;

        long now = System.nanoTime();
        if (lastFrameTime != 0) {
            long frameTime = now - lastFrameTime;
            frameTimes[sampleIndex % SAMPLE_SIZE] = frameTime;
            sampleIndex++;

            // Every 30 frames, analyze variance
            if (sampleIndex % SAMPLE_SIZE == 0) {
                analyzeFrameTimes();
            }
        }
        lastFrameTime = now;
    }

    @Unique
    private static void analyzeFrameTimes() {
        long sum = 0;
        long max = 0;
        for (long t : frameTimes) {
            sum += t;
            if (t > max) max = t;
        }
        long avg = sum / SAMPLE_SIZE;

        // Convert to ms
        long avgMs = avg / 1_000_000;
        long maxMs = max / 1_000_000;

        // If max frame is 3x the average, there's severe throttling
        boolean spiking = maxMs > avgMs * 3 && avgMs > 50;

        if (spiking && !G85PerfMod.thermalThrottleActive) {
            G85PerfMod.thermalThrottleActive = true;
            G85PerfMod.LOGGER.warn("[G85Perf] Frame spike detected: avg={}ms max={}ms → thermal mode ON",
                    avgMs, maxMs);
        } else if (!spiking && G85PerfMod.thermalThrottleActive && avgMs < 40) {
            G85PerfMod.thermalThrottleActive = false;
            G85PerfMod.LOGGER.info("[G85Perf] Stable frames: avg={}ms → thermal mode OFF", avgMs);
        }
    }
}
