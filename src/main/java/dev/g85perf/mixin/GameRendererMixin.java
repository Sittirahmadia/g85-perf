package dev.g85perf.mixin;

import dev.g85perf.G85PerfMod;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GameRendererMixin — Fixed for MC 1.21
 * render() signature: (RenderTickCounter, boolean) in 1.21
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    private long lastBgFrameTime = 0;
    private long lastThermalFrameTime = 0;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();

        // Background throttle (Zalith minimized)
        if (G85PerfMod.limitBgFps && !mc.isWindowFocused()) {
            long now = System.currentTimeMillis();
            long minFrame = 1000L / G85PerfMod.bgFpsCap;
            if (now - lastBgFrameTime < minFrame) {
                ci.cancel();
                return;
            }
            lastBgFrameTime = now;
            return;
        }

        // Thermal throttle frame cap
        if (G85PerfMod.thermalThrottleActive) {
            long now = System.currentTimeMillis();
            long minFrame = 1000L / G85PerfMod.thermalFpsCap;
            if (now - lastThermalFrameTime < minFrame) {
                ci.cancel();
                return;
            }
            lastThermalFrameTime = now;
        }
    }
}
