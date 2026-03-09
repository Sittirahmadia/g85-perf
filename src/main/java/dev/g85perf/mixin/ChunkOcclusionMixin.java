package dev.g85perf.mixin;

import dev.g85perf.G85PerfMod;
import net.minecraft.client.render.chunk.ChunkOcclusionData;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ChunkOcclusionMixin
 *
 * Mali-G52 benefits from fewer triangles submitted to the GPU.
 * Aggressive occlusion culling skips chunk sections that are not visible,
 * reducing GPU triangle count significantly in dense areas.
 *
 * This is especially effective in:
 * - Cave mining (surrounded by solid blocks)
 * - Dense forests
 * - Cities/structures
 */
@Mixin(ChunkOcclusionData.class)
public class ChunkOcclusionMixin {

    @Inject(method = "isVisibleThrough", at = @At("HEAD"), cancellable = true)
    private void onIsVisibleThrough(Direction from, Direction to,
                                     CallbackInfoReturnable<Boolean> cir) {
        // Same face = never visible through
        if (from == to) {
            cir.setReturnValue(false);
            return;
        }

        // During thermal throttle, cull even more aggressively
        if (G85PerfMod.thermalThrottleActive) {
            // Only allow straight-through visibility (NORTH→SOUTH, EAST→WEST, etc.)
            if (from.getOpposite() != to) {
                cir.setReturnValue(false);
            }
        }
    }
}
