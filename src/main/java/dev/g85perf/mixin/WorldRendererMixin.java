package dev.g85perf.mixin;

import dev.g85perf.G85PerfMod;
import net.minecraft.client.render.WorldRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WorldRendererMixin
 *
 * Reduces GPU workload on Mali-G52 by skipping expensive render passes.
 *
 * 1. Sky: render every N frames (barely visible at 30 FPS)
 * 2. Weather: skip rain/snow entirely during thermal throttle
 * 3. Chunk rebuilds: cap per tick to avoid stutter
 *
 * VirGL note: VirGL translates OpenGL → Gallium3D → host GPU.
 * Fewer draw calls = less VirGL overhead = smoother on Android.
 */
@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    private static int frameCount = 0;
    private static int rebuildsThisTick = 0;
    private static long lastTickTime = 0;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(CallbackInfo ci) {
        frameCount++;

        // Reset rebuild counter each tick (~50ms)
        long now = System.currentTimeMillis();
        if (now - lastTickTime >= 50) {
            rebuildsThisTick = 0;
            lastTickTime = now;
        }
    }

    /**
     * Skip sky render on alternating frames.
     * During thermal throttle, skip 3 out of 4 frames.
     */
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void onRenderSky(Matrix4f matrix, Matrix4f projectionMatrix,
                              float tickDelta, CallbackInfo ci) {
        int skipRate = G85PerfMod.thermalThrottleActive
                ? 4
                : G85PerfMod.skyRenderSkipFrames;

        if (frameCount % skipRate != 0) {
            ci.cancel();
        }
    }

    /**
     * Skip weather (rain/snow) during thermal throttle.
     * Weather is very expensive on Mali-G52 due to many transparent quads.
     */
    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void onRenderWeather(CallbackInfo ci) {
        if (G85PerfMod.reduceWeather && G85PerfMod.thermalThrottleActive) {
            ci.cancel();
        }
    }

    /**
     * Cap chunk rebuilds per tick.
     * Too many rebuilds at once = multi-frame stutter on G85.
     */
    @Inject(method = "scheduleChunkRender", at = @At("HEAD"), cancellable = true)
    private void onScheduleChunkRender(int x, int y, int z, boolean important, CallbackInfo ci) {
        if (important) return; // always allow important (player-triggered) rebuilds

        int cap = G85PerfMod.thermalThrottleActive
                ? 2
                : G85PerfMod.maxRebuildsPerTick;

        if (rebuildsThisTick >= cap) {
            ci.cancel();
        } else {
            rebuildsThisTick++;
        }
    }
}
