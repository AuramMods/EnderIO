package com.enderio.core.common.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/** Moves recipe inputs from the player's inventory into a server-approved menu slot range. */
public final class MenuRecipeTransfer {
    private MenuRecipeTransfer() {}

    public static void transfer(Player player, AbstractContainerMenu menu, List<Ingredient> ingredients,
            int firstSlot, int endSlot, boolean maximum) {
        if (player.isSpectator() || !menu.stillValid(player) || firstSlot < 0 || endSlot > menu.slots.size()
                || endSlot < firstSlot || ingredients.size() != endSlot - firstSlot) return;
        for (int targetIndex = firstSlot; targetIndex < endSlot; targetIndex++) {
            Slot target = menu.getSlot(targetIndex);
            Ingredient ingredient = ingredients.get(targetIndex - firstSlot);
            if (ingredient.isEmpty() || target.container == player.getInventory()) continue;
            for (Slot source : menu.slots) {
                if (source.container != player.getInventory() || !source.mayPickup(player)) continue;
                ItemStack available = source.getItem();
                ItemStack current = target.getItem();
                if (available.isEmpty() || !ingredient.test(available) || !target.mayPlace(available)
                        || (!current.isEmpty() && !ItemStack.isSameItemSameTags(current, available))) continue;
                int limit = Math.min(target.getMaxStackSize(available), maximum ? available.getMaxStackSize() : 1);
                int amount = Math.min(available.getCount(), limit - current.getCount());
                if (amount <= 0) break;
                ItemStack moved = source.remove(amount);
                if (moved.isEmpty()) continue;
                target.set(moved.copyWithCount(current.getCount() + moved.getCount()));
                source.onTake(player, moved);
                if (!maximum || target.getItem().getCount() >= limit) break;
            }
        }
        menu.broadcastChanges();
    }
}
