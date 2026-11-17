package com.enderio.enderio.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(FeatureFlagRegistry.class)
public interface FeatureFlagRegistryAccessor {
    @Accessor("names")
    Map<ResourceLocation, FeatureFlag> enderio$getFlags();
}
