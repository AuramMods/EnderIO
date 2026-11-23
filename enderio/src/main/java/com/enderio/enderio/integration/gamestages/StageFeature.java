package com.enderio.enderio.integration.gamestages;

import com.enderio.enderio.api.EnderIOAPI;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Locale;

public enum StageFeature {
    MACHINE_PLACEMENT,
    MACHINE_USE,
    CRAFTER_AUTOMATION,
    SOUL_CAPTURE,
    SOUL_SPAWNING,
    SOUL_DEVICE,
    OBELISK,
    TRAVEL,
    MOBILITY,
    CONDUIT_PLACEMENT,
    CONDUIT_CONFIGURATION,
    FIRE_CRAFTING;

    private final ResourceLocation id = EnderIOAPI.rl(name().toLowerCase(Locale.ROOT));

    public ResourceLocation id() {
        return id;
    }

    public static boolean isKnown(ResourceLocation id) {
        return Arrays.stream(values()).anyMatch(feature -> feature.id().equals(id));
    }
}
