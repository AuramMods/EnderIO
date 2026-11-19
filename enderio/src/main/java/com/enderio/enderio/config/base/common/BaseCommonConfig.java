package com.enderio.enderio.config.base.common;

import net.minecraftforge.common.ForgeConfigSpec;

public class BaseCommonConfig {
    public final BlocksConfig BLOCKS;
    public final ItemsConfig ITEMS;
    public final InfinityConfig INFINITY;
    public final EnchantmentsConfig ENCHANTMENTS;

    public BaseCommonConfig(ForgeConfigSpec.Builder builder) {
        BLOCKS = new BlocksConfig(builder);
        ITEMS = new ItemsConfig(builder);
        INFINITY = new InfinityConfig(builder);
        ENCHANTMENTS = new EnchantmentsConfig(builder);
    }
}
