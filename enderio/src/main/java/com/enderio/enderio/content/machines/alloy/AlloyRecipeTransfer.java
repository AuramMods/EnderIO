package com.enderio.enderio.content.machines.alloy;

import com.enderio.core.common.recipes.SizedIngredient;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Complete sized batches from the player's inventory and matching loaded inputs. */
public final class AlloyRecipeTransfer {
    private AlloyRecipeTransfer() {}

    public static boolean canLoad(AlloySmelterMenu menu, Player player, AlloySmeltingRecipe recipe) {
        return permitsMode(menu, recipe) && plan(menu, player, recipe.inputs(), false) != null;
    }

    public static void load(AlloySmelterMenu menu, Player player, AlloySmeltingRecipe recipe, boolean maximum) {
        if (!permitsMode(menu, recipe)) return;
        var moves = plan(menu, player, recipe.inputs(), maximum);
        if (moves == null) return;
        for (var move : moves) {
            var target = move.target;
            int remaining = move.amount;
            var result = target.getItem().copy();
            for (var source : move.stock.sources) {
                if (remaining == 0) break;
                var removed = source.remove(Math.min(remaining, source.getItem().getCount()));
                if (removed.isEmpty()) continue;
                if (result.isEmpty()) result = removed.copy();
                else result.grow(removed.getCount());
                remaining -= removed.getCount();
                source.onTake(player, removed);
            }
            target.set(result);
        }
        menu.broadcastChanges();
    }

    private static boolean permitsMode(AlloySmelterMenu menu, AlloySmeltingRecipe recipe) {
        return recipe.isSmelting() ? menu.getMode().canSmelt() : menu.getMode().canAlloy();
    }

    private static @Nullable List<Move> plan(AlloySmelterMenu menu, Player player, List<SizedIngredient> inputs,
            boolean maximum) {
        if (inputs.isEmpty() || inputs.size() > AlloySmelterMenu.INPUT_COUNT) return null;
        var targets = menu.slots.subList(AlloySmelterMenu.INPUTS_INDEX,
            AlloySmelterMenu.INPUTS_INDEX + AlloySmelterMenu.INPUT_COUNT);
        if (targets.subList(inputs.size(), targets.size()).stream().anyMatch(Slot::hasItem)) return null;

        var stock = new ArrayList<Stock>();
        for (var slot : menu.slots) {
            if (slot.container != player.getInventory() || !slot.hasItem() || !slot.mayPickup(player)) continue;
            var item = slot.getItem();
            var group = stock.stream().filter(entry -> ItemStack.isSameItemSameTags(entry.item, item)).findFirst().orElse(null);
            if (group == null) {
                group = new Stock(item.copyWithCount(1));
                stock.add(group);
            }
            group.sources.add(slot);
            group.available += item.getCount();
        }

        var slots = new ArrayList<Target>();
        int batchLimit = Integer.MAX_VALUE;
        for (int i = 0; i < inputs.size(); i++) {
            var slot = targets.get(i);
            var input = inputs.get(i);
            var current = slot.getItem();
            if (!current.isEmpty() && !input.ingredient().test(current)) return null;
            var candidates = stock.stream().filter(entry -> input.ingredient().test(entry.item)
                && (current.isEmpty() || ItemStack.isSameItemSameTags(entry.item, current))).toList();
            slots.add(new Target(slot, input.count(), current.getCount(), candidates));
            batchLimit = Math.min(batchLimit, slot.getMaxStackSize() / input.count());
        }

        List<Move> best = null;
        int low = 1;
        int high = maximum ? batchLimit : Math.min(batchLimit, 1);
        while (low <= high) {
            int batches = low + (high - low) / 2;
            var moves = new ArrayList<Move>();
            if (fill(slots, batches, 0, moves)) {
                best = moves;
                low = batches + 1;
            } else {
                high = batches - 1;
            }
        }
        return best;
    }

    private static boolean fill(List<Target> slots, int batches, int index, List<Move> moves) {
        if (index == slots.size()) return true;
        var target = slots.get(index);
        int required = target.count * batches;
        int remaining = required - target.loaded;
        if (remaining <= 0) return fill(slots, batches, index + 1, moves);

        for (var stock : target.candidates) {
            int capacity = Math.min(target.slot.getMaxStackSize(), stock.item.getMaxStackSize());
            if (stock.available < remaining || capacity < required) continue;
            stock.available -= remaining;
            moves.add(new Move(target.slot, stock, remaining));
            boolean complete = fill(slots, batches, index + 1, moves);
            stock.available += remaining;
            if (complete) return true;
            moves.remove(moves.size() - 1);
        }
        return false;
    }

    private static final class Stock {
        final ItemStack item;
        final List<Slot> sources = new ArrayList<>();
        int available;

        Stock(ItemStack item) { this.item = item; }
    }

    private record Target(Slot slot, int count, int loaded, List<Stock> candidates) {}
    private record Move(Slot target, Stock stock, int amount) {}
}
