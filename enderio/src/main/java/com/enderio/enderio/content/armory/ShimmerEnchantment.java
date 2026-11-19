package com.enderio.enderio.content.armory;

import com.enderio.enderio.config.base.BaseConfig;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public final class ShimmerEnchantment extends Enchantment {
    public ShimmerEnchantment() {
        super(Rarity.VERY_RARE, EnchantmentCategory.VANISHABLE, EquipmentSlot.values());
    }

    @Override
    public int getMinCost(int level) {
        return BaseConfig.COMMON.ENCHANTMENTS.SHIMMER_MIN_COST.get();
    }

    @Override
    public int getMaxCost(int level) {
        return BaseConfig.COMMON.ENCHANTMENTS.SHIMMER_MAX_COST.get();
    }

    @Override
    public boolean isTreasureOnly() {
        return true;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return false;
    }

    @Override
    public boolean isDiscoverable() {
        return false;
    }

    @Override
    public boolean isTradeable() {
        return false;
    }
}
