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
 * ThermalThrottleMixin
 * Detects thermal throttling on Helio G85 via frame time variance.
 */
@Mixin(value = GameRenderer.class, priority = 900)
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
            if (sampleIndex % SAMPLE_SIZE == 0) analyzeFrameTimes();
        }
        lastFrameTime = now;
    }

    @Unique
    private static void analyzeFrameTimes() {
        long sum = 0, max = 0;
        for (long t : frameTimes) { sum += t; if (t > max) max = t; }
        long avgMs = (sum / SAMPLE_SIZE) / 1_000_000;
        long maxMs = max / 1_000_000;
        boolean spiking = maxMs > avgMs * 3 && avgMs > 50;

        if (spiking && !G85PerfMod.thermalThrottleActive) {
            G85PerfMod.thermalThrottleActive = true;
            G85PerfMod.LOGGER.warn("[G85Perf] Thermal ON — avg={}ms max={}ms", avgMs, maxMs);
        } else if (!spiking && G85PerfMod.thermalThrottleActive && avgMs < 40) {
            G85PerfMod.thermalThrottleActive = false;
            G85PerfMod.LOGGER.info("[G85Perf] Thermal OFF — avg={}ms", avgMs);
        }
    }
}
