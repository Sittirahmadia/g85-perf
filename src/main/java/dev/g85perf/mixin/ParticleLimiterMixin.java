package dev.g85perf.mixin;

import dev.g85perf.G85PerfMod;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ParticleLimiterMixin
 *
 * Mali-G52 struggles with many overdraw layers (transparent quads on top of each other).
 * Particles = tons of overdraw. Reducing particles dramatically helps Mali GPUs.
 *
 * Optimizations:
 * - Skip every N particles (particleSkipRate)
 * - Hard cap at maxParticles total
 * - Extra aggressive skip when thermal throttle is active
 */
@Mixin(ParticleManager.class)
public class ParticleLimiterMixin {

    private static int spawnCount = 0;
    private static int activeEstimate = 0;

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddParticle(Particle particle, CallbackInfo ci) {

        // Extra aggressive during thermal throttle
        int skipRate = G85PerfMod.thermalThrottleActive
                ? G85PerfMod.particleSkipRate * 2
                : G85PerfMod.particleSkipRate;

        spawnCount++;

        // Skip based on rate
        if (spawnCount % skipRate != 0) {
            ci.cancel();
            return;
        }

        // Hard cap
        activeEstimate++;
        if (activeEstimate > G85PerfMod.maxParticles) {
            activeEstimate = G85PerfMod.maxParticles;
            ci.cancel();
            return;
        }

        // Reset estimate periodically
        if (spawnCount > 10000) {
            spawnCount = 0;
            activeEstimate = Math.max(0, activeEstimate - 10);
        }
    }
}
