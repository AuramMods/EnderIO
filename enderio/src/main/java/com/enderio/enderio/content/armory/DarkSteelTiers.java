package com.enderio.enderio.content.armory;

import com.enderio.enderio.foundation.tag.EIOTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ForgeTier;

public class DarkSteelTiers {
    public static final Tier DARK_STEEL_TIER = new ForgeTier(3, 2000,
        8.0f, 3.0f, 25, net.minecraft.tags.BlockTags.NEEDS_DIAMOND_TOOL, () -> Ingredient.of(EIOTags.Items.INGOTS_DARK_STEEL));
}
