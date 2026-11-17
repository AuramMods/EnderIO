package com.enderio.enderio.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlagRegistry;
import net.minecraft.world.flag.FeatureFlags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Registers Ender IO's opt-in flags before vanilla freezes the shared flag universe. */
@Mixin(FeatureFlags.class)
public abstract class FeatureFlagsMixin {
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/flag/FeatureFlagRegistry$Builder;build()Lnet/minecraft/world/flag/FeatureFlagRegistry;"),
        require = 1)
    private static FeatureFlagRegistry enderio$registerFlags(FeatureFlagRegistry.Builder builder) {
        builder.create(ResourceLocation.fromNamespaceAndPath("enderio", "farming_station"));
        builder.create(ResourceLocation.fromNamespaceAndPath("enderio", "enderface"));
        builder.create(ResourceLocation.fromNamespaceAndPath("enderio", "niard"));
        return builder.build();
    }
}
