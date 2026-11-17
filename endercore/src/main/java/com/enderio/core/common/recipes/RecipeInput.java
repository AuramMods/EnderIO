package com.enderio.core.common.recipes;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A recipe's read-only view of inputs, exposed through the native 1.20.1 Container contract. */
public interface RecipeInput extends Container {
    int size();
    @Override
    default int getContainerSize() { return size(); }
    @Override
    default boolean isEmpty() {
        for (int i = 0; i < size(); i++) if (!getItem(i).isEmpty()) return false;
        return true;
    }
    @Override
    default ItemStack removeItem(int slot, int count) { throw readOnly(); }
    @Override
    default ItemStack removeItemNoUpdate(int slot) { throw readOnly(); }
    @Override
    default void setItem(int slot, ItemStack stack) { throw readOnly(); }
    @Override
    default void setChanged() { throw readOnly(); }
    @Override
    default void clearContent() { throw readOnly(); }
    @Override
    default boolean stillValid(Player player) { return true; }
    private static UnsupportedOperationException readOnly() { return new UnsupportedOperationException("Recipe input views cannot mutate an inventory"); }
}
