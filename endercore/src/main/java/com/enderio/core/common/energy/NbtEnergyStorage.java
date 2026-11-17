package com.enderio.core.common.energy;

import com.enderio.core.common.item.ItemDataKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;

/** A live Forge energy capability whose authoritative state is the owning item's NBT. */
public class NbtEnergyStorage implements IEnergyStorage {
    protected final ItemStack stack;
    private final ItemDataKey<Integer> key;
    private final int capacity;
    private final int maxReceive;
    private final int maxExtract;

    public NbtEnergyStorage(ItemStack stack, ItemDataKey<Integer> key, int capacity) {
        this(stack, key, capacity, capacity, capacity);
    }

    public NbtEnergyStorage(ItemStack stack, ItemDataKey<Integer> key, int capacity, int maxReceive, int maxExtract) {
        if (capacity < 0 || maxReceive < 0 || maxExtract < 0) {
            throw new IllegalArgumentException("Energy capacity and transfer limits must be nonnegative");
        }
        this.stack = stack;
        this.key = key;
        this.capacity = capacity;
        this.maxReceive = maxReceive;
        this.maxExtract = maxExtract;
    }

    @Override
    public int receiveEnergy(int requested, boolean simulate) {
        if (requested <= 0 || !canReceive()) return 0;
        int stored = getEnergyStored();
        int received = Math.min(requested, Math.min(maxReceive, Math.max(0, capacity - stored)));
        if (!simulate && received > 0) key.set(stack, stored + received);
        return received;
    }

    @Override
    public int extractEnergy(int requested, boolean simulate) {
        if (requested <= 0 || !canExtract()) return 0;
        int stored = getEnergyStored();
        int extracted = Math.min(requested, Math.min(maxExtract, stored));
        if (!simulate && extracted > 0) key.set(stack, stored - extracted);
        return extracted;
    }

    @Override
    public int getEnergyStored() {
        return Math.max(0, key.getOrDefault(stack, 0));
    }

    @Override
    public int getMaxEnergyStored() {
        return capacity;
    }

    @Override
    public boolean canExtract() {
        return !stack.isEmpty() && maxExtract > 0;
    }

    @Override
    public boolean canReceive() {
        return !stack.isEmpty() && maxReceive > 0;
    }
}
