package com.enderio.enderio.content.armory;

import com.enderio.enderio.EnderIO;
import com.enderio.enderio.init.EIOEnchantments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = EnderIO.MOD_ID)
public final class SoulboundEnchantment extends Enchantment {
    private static final String RETAINED_ITEMS = "enderio:soulbound_items";

    public SoulboundEnchantment() {
        super(Rarity.VERY_RARE, EnchantmentCategory.VANISHABLE, EquipmentSlot.values());
    }

    @Override
    public int getMinCost(int level) {
        return 16;
    }

    @Override
    public int getMaxCost(int level) {
        return 60;
    }

    @Override
    public boolean isDiscoverable() {
        return false;
    }

    @Override
    public boolean isTradeable() {
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void retainDeathDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer
            || player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }

        var data = player.getPersistentData();
        var persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        var retained = persisted.getList(RETAINED_ITEMS, Tag.TAG_COMPOUND);
        var drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            var stack = drops.next().getItem();
            if (stack.getEnchantmentLevel(EIOEnchantments.SOULBOUND.get()) > 0) {
                retained.add(stack.save(new CompoundTag()));
                drops.remove();
            }
        }
        if (!retained.isEmpty()) {
            // Forge saves and copies this data even when the player disconnects before respawning.
            persisted.put(RETAINED_ITEMS, retained);
            data.put(Player.PERSISTED_NBT_TAG, persisted);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            restoreItems(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.isDeadOrDying()) {
            restoreItems(player);
        }
    }

    private static void restoreItems(ServerPlayer player) {
        var persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        var retained = persisted.getList(RETAINED_ITEMS, Tag.TAG_COMPOUND);
        persisted.remove(RETAINED_ITEMS);
        for (int i = 0; i < retained.size(); i++) {
            var stack = ItemStack.of(retained.getCompound(i));
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
    }
}
