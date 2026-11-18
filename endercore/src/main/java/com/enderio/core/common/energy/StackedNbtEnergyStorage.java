package com.enderio.core.common.energy;

import com.enderio.core.common.item.ItemDataKey;
import net.minecraft.world.item.ItemStack;

/** Keeps identical per-item charge in a stack, transferring only whole stack-wide increments. */
public final class StackedNbtEnergyStorage extends NbtEnergyStorage {
    public StackedNbtEnergyStorage(ItemStack stack, ItemDataKey<Integer> key, int capacity) {
        super(stack, key, capacity);
    }

    private StackedNbtEnergyStorage(ItemStack stack, ItemDataKey<Integer> key, int capacity, LegacyCharge legacyCharge) {
        super(stack, key, capacity, capacity, capacity, legacyCharge);
    }

    /** For block items whose previous Forge release stored energy inside their BlockEntityTag. */
    public static StackedNbtEnergyStorage withForge6BlockEntityCharge(ItemStack stack, ItemDataKey<Integer> key, int capacity) {
        return new StackedNbtEnergyStorage(stack, key, capacity, LegacyCharge.BLOCK_ENTITY);
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
