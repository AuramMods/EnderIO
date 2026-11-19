package com.enderio.enderio.content.armory;

import com.enderio.core.common.util.TeleportUtils;
import com.enderio.enderio.config.base.BaseConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public final class RepellentEnchantment extends Enchantment {
    public RepellentEnchantment() {
        super(Rarity.VERY_RARE, EnchantmentCategory.ARMOR,
            new EquipmentSlot[] { EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET });
    }

    @Override
    public int getMaxLevel() {
        return BaseConfig.COMMON.ENCHANTMENTS.REPELLENT_MAX_LEVEL.get();
    }

    @Override
    public int getMinCost(int level) {
        var config = BaseConfig.COMMON.ENCHANTMENTS;
        return config.REPELLENT_MIN_COST_BASE.get() + config.REPELLENT_MIN_COST_MULT.get() * level;
    }

    @Override
    public int getMaxCost(int level) {
        var config = BaseConfig.COMMON.ENCHANTMENTS;
        return config.REPELLENT_MAX_COST_BASE.get() + config.REPELLENT_MAX_COST_MULT.get() * level;
    }

    @Override
    public boolean isDiscoverable() {
        return false;
    }

    @Override
    public boolean isTradeable() {
        return false;
    }

    @Override
    public void doPostHurt(LivingEntity wearer, Entity source, int level) {
        if (wearer.level().isClientSide || !(wearer instanceof Player) || !(source instanceof LivingEntity attacker)) {
            return;
        }
        var config = BaseConfig.COMMON.ENCHANTMENTS;
        var random = wearer.getRandom();
        float chance = config.REPELLENT_CHANCE_BASE.get().floatValue() + config.REPELLENT_CHANCE_MULT.get().floatValue() * level;
        if (random.nextFloat() < chance
            && (attacker instanceof Player || random.nextFloat() < config.REPELLENT_NON_PLAYER_CHANCE.get())) {
            double range = config.REPELLENT_RANGE_BASE.get() + config.REPELLENT_RANGE_MULT.get() * level;
            TeleportUtils.randomTeleport(attacker, range);
        }
    }
}
