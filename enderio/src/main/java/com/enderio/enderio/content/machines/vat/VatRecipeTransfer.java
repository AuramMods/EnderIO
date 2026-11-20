package com.enderio.enderio.content.machines.vat;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Plans complete VAT batches from the two machine slots and the player's finite inventory. */
public final class VatRecipeTransfer {
    private VatRecipeTransfer() { }

    public record Missing(boolean first, boolean second) {
        private int count() {
            return (first ? 1 : 0) + (second ? 1 : 0);
        }
    }

    public static Missing missingReagents(VatMenu menu, Player player, FermentingRecipe recipe) {
        var layouts = layouts(menu, player, recipe);
        if (layouts.forward.best(1) != null || layouts.reversed.best(1) != null) {
            return new Missing(false, false);
        }
        var forward = layouts.forward.missing();
        var reversed = layouts.reversed.missing();
        return forward.count() <= reversed.count() ? forward : reversed;
    }

    public static void transfer(VatMenu menu, Player player, FermentingRecipe recipe, boolean maximum) {
        var layouts = layouts(menu, player, recipe);
        int limit = maximum ? Integer.MAX_VALUE : 1;
        var selected = layouts.forward.best(limit);
        var reversed = layouts.reversed.best(limit);
        if (reversed != null && (selected == null || reversed.batches > selected.batches)) {
            selected = reversed;
        }
        if (selected == null) return;
        move(player, menu.getSlot(VatMenu.INPUTS_INDEX), selected.left, selected.batches);
        move(player, menu.getSlot(VatMenu.INPUTS_INDEX + 1), selected.right, selected.batches);
        menu.broadcastChanges();
    }

    private static Layouts layouts(VatMenu menu, Player player, FermentingRecipe recipe) {
        var stock = new ArrayList<Stock>();
        for (var slot : menu.slots) {
            if (slot.container != player.getInventory() || !slot.hasItem() || !slot.mayPickup(player)) continue;
            var stack = slot.getItem();
            var group = stock.stream().filter(candidate -> ItemStack.isSameItemSameTags(candidate.stack, stack)).findFirst().orElse(null);
            if (group == null) {
                group = new Stock(stack.copyWithCount(1));
                stock.add(group);
            }
            group.sources.add(slot);
            group.count += stack.getCount();
        }
        var left = menu.getSlot(VatMenu.INPUTS_INDEX);
        var right = menu.getSlot(VatMenu.INPUTS_INDEX + 1);
        return new Layouts(
            new Layout(candidates(left, recipe.firstReagent(), stock), candidates(right, recipe.secondReagent(), stock), false),
            new Layout(candidates(left, recipe.secondReagent(), stock), candidates(right, recipe.firstReagent(), stock), true));
    }

    private static List<Candidate> candidates(Slot target, TagKey<Item> reagent, List<Stock> stock) {
        var current = target.getItem();
        int loaded = current.getCount();
        if (loaded > 0) {
            if (!current.is(reagent)) return List.of();
            for (var group : stock) {
                if (ItemStack.isSameItemSameTags(current, group.stack) && target.mayPlace(group.stack)) {
                    return List.of(candidate(target, group, loaded));
                }
            }
            return List.of(new Candidate(null, loaded, loaded));
        }

        var candidates = new ArrayList<Candidate>();
        for (var group : stock) {
            if (group.stack.is(reagent) && target.mayPlace(group.stack)) {
                var candidate = candidate(target, group, 0);
                if (candidate.limit > 0) candidates.add(candidate);
            }
        }
        return candidates;
    }

    private static Candidate candidate(Slot target, Stock stock, int loaded) {
        int capacity = Math.min(target.getMaxStackSize(), stock.stack.getMaxStackSize());
        int maximum = Math.max(loaded, Math.min(capacity, loaded + stock.count));
        return new Candidate(stock, loaded, maximum);
    }

    private static void move(Player player, Slot target, Candidate candidate, int batches) {
        int remaining = batches - candidate.loaded;
        if (remaining <= 0) return;
        var result = target.getItem().copy();
        for (var source : candidate.stock.sources) {
            if (remaining == 0) break;
            var moved = source.remove(Math.min(remaining, source.getItem().getCount()));
            if (moved.isEmpty()) continue;
            if (result.isEmpty()) result = moved.copy();
            else result.grow(moved.getCount());
            remaining -= moved.getCount();
            source.onTake(player, moved);
        }
        target.set(result);
    }

    private static final class Stock {
        final ItemStack stack;
        final List<Slot> sources = new ArrayList<>();
        int count;

        Stock(ItemStack stack) {
            this.stack = stack;
        }
    }

    private record Candidate(Stock stock, int loaded, int limit) { }
    private record Plan(Candidate left, Candidate right, int batches) { }
    private record Layouts(Layout forward, Layout reversed) { }

    private record Layout(List<Candidate> left, List<Candidate> right, boolean reversed) {
        Plan best(int limit) {
            Plan best = null;
            for (var a : left) {
                for (var b : right) {
                    int batches = Math.min(limit, Math.min(a.limit, b.limit));
                    if (a.stock != null && a.stock == b.stock) {
                        // Each batch needs two items. The individual limits also account for unequal preloaded counts.
                        batches = Math.min(batches, (a.stock.count + a.loaded + b.loaded) / 2);
                    }
                    if (batches > 0 && (best == null || batches > best.batches)) {
                        best = new Plan(a, b, batches);
                        if (batches == limit) return best;
                    }
                }
            }
            return best;
        }

        Missing missing() {
            boolean missingLeft = left.isEmpty();
            // If both slots have candidates but no pair, they are competing for a single available item.
            boolean missingRight = right.isEmpty() || !missingLeft;
            return reversed ? new Missing(missingRight, missingLeft) : new Missing(missingLeft, missingRight);
        }
    }
}
