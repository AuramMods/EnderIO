package com.enderio.enderio.content.armory;

import com.enderio.enderio.config.base.BaseConfig;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

public final class WitheringEnchantment extends Enchantment {
    private static final EnchantmentCategory WEAPONS = EnchantmentCategory.create("ENDERIO_WITHERING",
        item -> EnchantmentCategory.WEAPON.canEnchant(item) || EnchantmentCategory.BOW.canEnchant(item)
            || EnchantmentCategory.CROSSBOW.canEnchant(item));

    public WitheringEnchantment() {
        super(Rarity.UNCOMMON, WEAPONS, new EquipmentSlot[] { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND });
    }

    @Override
    public int getMinCost(int level) {
        return BaseConfig.COMMON.ENCHANTMENTS.WITHERING_BLADE_MIN_COST.get();
    }

    @Override
    public int getMaxCost(int level) {
        return BaseConfig.COMMON.ENCHANTMENTS.WITHERING_BLADE_MAX_COST.get();
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
    public void doPostAttack(LivingEntity attacker, Entity target, int level) {
        if (!attacker.level().isClientSide && target instanceof LivingEntity living
            && attacker.getMainHandItem().getEnchantmentLevel(this) > 0) {
            living.addEffect(new MobEffectInstance(MobEffects.WITHER, 200));
        }
    }
}
