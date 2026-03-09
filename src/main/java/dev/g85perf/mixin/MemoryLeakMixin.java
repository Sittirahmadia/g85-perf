package dev.g85perf.mixin;

import dev.g85perf.G85PerfMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MemoryLeakMixin
 *
 * Android has strict memory limits (Android kills apps using too much RAM).
 * This mixin hooks into world load/unload to aggressively free memory.
 *
 * On G85 with 4GB RAM (shared with Android OS), the JVM typically gets
 * ~1-1.5GB. Without cleanup, GC pauses cause random stutters mid-game.
 */
@Mixin(MinecraftClient.class)
public class MemoryLeakMixin {

    /**
     * When a world is loaded, log memory state.
     */
    @Inject(method = "joinWorld", at = @At("RETURN"))
    private void onJoinWorld(ClientWorld world, CallbackInfo ci) {
        Runtime rt = Runtime.getRuntime();
        long usedMB  = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long totalMB = rt.maxMemory() / 1024 / 1024;
        G85PerfMod.LOGGER.info("[G85Perf] World loaded — RAM: {}MB / {}MB", usedMB, totalMB);

        // Run GC after world load (world load = massive allocation spike)
        System.gc();
    }

    /**
     * When disconnecting, force GC to reclaim world data memory fast.
     * Without this, Android may kill Zalith while MC is still GCing.
     */
    @Inject(method = "disconnect()V", at = @At("RETURN"))
    private void onDisconnect(CallbackInfo ci) {
        G85PerfMod.LOGGER.info("[G85Perf] Disconnected — running GC cleanup");
        System.gc();
        System.runFinalization();
    }
}
