package com.enderio.core.common.energy;

import com.enderio.core.common.item.ItemDataKey;
import net.minecraft.world.item.ItemStack;

/** Keeps identical per-item charge in a stack, transferring only whole stack-wide increments. */
public final class StackedNbtEnergyStorage extends NbtEnergyStorage {
    public StackedNbtEnergyStorage(ItemStack stack, ItemDataKey<Integer> key, int capacity) {
        super(stack, key, capacity);
    }

    @Override
    public int receiveEnergy(int requested, boolean simulate) {
        int count = stack.getCount();
        return count <= 0 || requested <= 0 ? 0 : super.receiveEnergy(requested / count, simulate) * count;
    }

    @Override
    public int extractEnergy(int requested, boolean simulate) {
        int count = stack.getCount();
        return count <= 0 || requested <= 0 ? 0 : super.extractEnergy(requested / count, simulate) * count;
    }
}
