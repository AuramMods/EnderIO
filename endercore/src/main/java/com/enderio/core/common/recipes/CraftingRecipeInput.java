package com.enderio.core.common.recipes;

import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** A machine's crafting-grid snapshot, usable by native vanilla and Forge crafting recipes. */
public record CraftingRecipeInput(int width, int height, List<ItemStack> items) implements CraftingContainer, RecipeInput {
    public CraftingRecipeInput {
        if (width <= 0 || height <= 0 || (long) width * height != items.size()) {
            throw new IllegalArgumentException("Crafting grid dimensions do not match its slots");
        }
        items = items.stream().map(ItemStack::copy).toList();
    }

    @Override
    public int getWidth() { return width; }
    @Override
    public int getHeight() { return height; }
    @Override
    public List<ItemStack> getItems() { return items; }
    @Override
    public int size() { return items.size(); }
    @Override
    public ItemStack getItem(int slot) { return items.get(slot); }
    @Override
    public void fillStackedContents(StackedContents contents) {
        items.forEach(contents::accountSimpleStack);
    }
}
