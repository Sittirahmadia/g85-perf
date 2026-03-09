package dev.g85perf.mixin;

import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * GameOptionsAccessor
 *
 * Exposes the private cloudRenderMode field from GameOptions
 * so we can disable clouds programmatically for Mali-G52 performance.
 */
@Mixin(GameOptions.class)
public interface GameOptionsAccessor {

    @Accessor("cloudRenderMode")
    SimpleOption<CloudRenderMode> getCloudRenderMode();
}
