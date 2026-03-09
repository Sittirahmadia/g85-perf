package dev.g85perf.mixin;

import dev.g85perf.G85PerfMod;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * ChunkBuilderThreadMixin
 *
 * G85 has 4x Cortex-A75 (performance) + 4x Cortex-A55 (efficiency).
 * Vanilla Minecraft uses only 1-2 threads for chunk mesh building.
 *
 * We use 3 threads (leaving 1 A75 free for game logic + UI thread).
 * This causes chunks to appear ~3x faster when moving through the world.
 *
 * Note: Don't use all 8 cores — Android needs cores for system tasks.
 */
@Mixin(ChunkBuilder.class)
public class ChunkBuilderThreadMixin {

    @ModifyArg(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/concurrent/Executors;newFixedThreadPool(ILjava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ExecutorService;"
        ),
        index = 0
    )
    private int modifyChunkBuilderThreads(int original) {
        int target = G85PerfMod.chunkBuilderThreads;
        G85PerfMod.LOGGER.info("[G85Perf] ChunkBuilder threads: {} → {}", original, target);
        return target;
    }
}
