package com.enderio.enderio.config.base.common;

import net.minecraftforge.common.ForgeConfigSpec;

public class InfinityConfig {
    public final ForgeConfigSpec.BooleanValue MAKES_SOUND;
    public final ForgeConfigSpec.IntValue FIRE_MIN_AGE;
    public final ForgeConfigSpec.IntValue MAX_TRACKED_FIRES;

    public InfinityConfig(ForgeConfigSpec.Builder builder) {
        builder.push("grainsOfInfinity");

        MAKES_SOUND = builder.comment("Should it make a sound when Grains of Infinity drops from a fire?").define("makesSound", true);
        FIRE_MIN_AGE = builder.comment("How old (in ticks) does a fire have to be to be able to spawn Infinity Powder?").defineInRange("fireMinAge", 260, 1, 1000);
        MAX_TRACKED_FIRES = builder
            .comment("The maximum number of fires being tracked for fire crafting per-dimension. Once exceeded, older fires are pruned first.")
            .defineInRange("maxTrackedFires", 500, 1, Integer.MAX_VALUE);

        builder.pop();
    }
}
