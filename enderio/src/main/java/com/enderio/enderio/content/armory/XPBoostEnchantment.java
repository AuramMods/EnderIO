package com.enderio.enderio.content.armory;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.enderio.config.base.BaseConfig;
import com.enderio.enderio.init.EIOEnchantments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EnderIOAPI.MOD_ID)
public final class XPBoostEnchantment extends Enchantment {
    private static final String ARROW_BOOST_LEVEL = "EnderIOXpBoostLevel";
    private static final String FATAL_ARROW_LEVEL = "enderio:xp_boost_fatal_arrow";
    private static final EnchantmentCategory TOOLS = EnchantmentCategory.create("ENDERIO_XP_BOOST",
        item -> item.getDefaultInstance().isDamageableItem() && !(item instanceof ArmorItem) && !(item instanceof FishingRodItem));

    public XPBoostEnchantment() {
        super(Rarity.COMMON, TOOLS, new EquipmentSlot[] { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND });
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinCost(int level) {
        var config = BaseConfig.COMMON.ENCHANTMENTS;
        return config.XP_BOOST_MIN_COST_BASE.get() + config.XP_BOOST_MIN_COST_MULT.get() * level;
    }

    @Override
    public int getMaxCost(int level) {
        var config = BaseConfig.COMMON.ENCHANTMENTS;
        return config.XP_BOOST_MAX_COST_BASE.get() + config.XP_BOOST_MAX_COST_MULT.get() * level;
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
    protected boolean checkCompatibility(Enchantment other) {
        return other != Enchantments.SILK_TOUCH && super.checkCompatibility(other);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        int experience = event.getExpToDrop();
        int level = getActiveLevel(event.getPlayer());
        if (experience > 0 && level > 0) {
            event.setExpToDrop(addBonus(experience, experience, level));
        }
    }

    @SubscribeEvent
    public static void onArrowJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || event.loadedFromDisk() || !(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        int level = getActiveLevel(arrow.getOwner());
        if (level > 0) {
            arrow.getPersistentData().putInt(ARROW_BOOST_LEVEL, level);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        // Vanilla drops experience before updating the entity's last-damage-source field.
        var data = entity.getPersistentData();
        if (event.getSource().getDirectEntity() instanceof Arrow arrow) {
            data.putInt(FATAL_ARROW_LEVEL, arrow.getPersistentData().getInt(ARROW_BOOST_LEVEL));
        } else {
            data.remove(FATAL_ARROW_LEVEL);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMobExperience(LivingExperienceDropEvent event) {
        var data = event.getEntity().getPersistentData();
        boolean hitByArrow = data.contains(FATAL_ARROW_LEVEL);
        int arrowLevel = data.getInt(FATAL_ARROW_LEVEL);
        data.remove(FATAL_ARROW_LEVEL);
        if (!(event.getAttackingPlayer() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        int experience = event.getDroppedExperience();
        if (experience <= 0) {
            return;
        }
        int level = hitByArrow ? arrowLevel : getActiveLevel(player);
        if (level > 0) {
            event.setDroppedExperience(addBonus(experience, event.getOriginalExperience(), level));
        }
    }

    private static int getActiveLevel(Entity entity) {
        if (!(entity instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return 0;
        }
        var tool = player.getMainHandItem();
        return tool.getEnchantmentLevel(Enchantments.SILK_TOUCH) == 0 ? tool.getEnchantmentLevel(EIOEnchantments.XP_BOOST.get()) : 0;
    }

    private static int addBonus(int experience, int baseExperience, int level) {
        int bonus = Math.round(baseExperience * ((float) Math.log10(level + 1.0) * 2));
        return (int) Math.min((long) experience + bonus, Integer.MAX_VALUE);
    }
}
