package com.enderio.enderio.init;

import com.enderio.enderio.EnderIO;
import com.enderio.enderio.mixin.FeatureFlagRegistryAccessor;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlags;

import java.util.Objects;

public class EIOFeatureFlags {
    public static final FeatureFlag ENDERFACE = getFlag("enderface");
    public static final FeatureFlag NIARD = getFlag("niard");

    private static FeatureFlag getFlag(String name) {
        return Objects.requireNonNull(((FeatureFlagRegistryAccessor) FeatureFlags.REGISTRY)
            .enderio$getFlags().get(EnderIO.rl(name)), "Unregistered Ender IO feature flag: " + name);
    }
}
