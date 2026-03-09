package dev.g85perf.mixin;

import dev.g85perf.G85PerfMod;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EntityCullMixin
 *
 * Mali-G52 has limited geometry throughput (draw calls are expensive).
 * Every entity = multiple draw calls. Culling invisible entities = big win.
 *
 * Culling strategies:
 * 1. Distance cull: skip entities beyond maxEntityRenderDist
 * 2. Back-face cull: skip entities >120° behind player view direction
 * 3. Thermal cull: during throttle, reduce distance further to 12 blocks
 */
@Mixin(EntityRenderer.class)
public class EntityCullMixin<T extends Entity> {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderEntity(T entity, float yaw, float tickDelta,
                                MatrixStack matrices,
                                VertexConsumerProvider vertexConsumers,
                                int light, CallbackInfo ci) {

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        double maxDist = G85PerfMod.thermalThrottleActive
                ? 12.0  // very aggressive during thermal throttle
                : G85PerfMod.maxEntityRenderDist;

        double distSq = mc.player.squaredDistanceTo(entity);

        // Distance cull
        if (distSq > maxDist * maxDist) {
            ci.cancel();
            return;
        }

        // Back-face cull (skip entities behind player)
        if (G85PerfMod.entityBackCull && distSq > 8 * 8) {
            Vec3d look     = mc.player.getRotationVector();
            Vec3d toEntity = entity.getPos()
                    .subtract(mc.player.getEyePos())
                    .normalize();

            // dot < -0.4 means entity is roughly behind the player (>114°)
            if (look.dotProduct(toEntity) < -0.4) {
                ci.cancel();
            }
        }
    }
}
