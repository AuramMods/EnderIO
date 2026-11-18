package com.enderio.core.common.energy;

import com.enderio.core.CoreNBTKeys;
import com.enderio.core.common.item.ItemDataKey;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;

/** A live Forge energy capability whose authoritative state is the owning item's NBT. */
public class NbtEnergyStorage implements IEnergyStorage {
    protected final ItemStack stack;
    private final ItemDataKey<Integer> key;
    private final int capacity;
    private final int maxReceive;
    private final int maxExtract;
    private final boolean readForge6Charge;

    public NbtEnergyStorage(ItemStack stack, ItemDataKey<Integer> key, int capacity) {
        this(stack, key, capacity, capacity, capacity);
    }

    public NbtEnergyStorage(ItemStack stack, ItemDataKey<Integer> key, int capacity, int maxReceive, int maxExtract) {
        this(stack, key, capacity, maxReceive, maxExtract, false);
    }

    /** For items whose previous Forge release wrote a native Energy compound. */
    public static NbtEnergyStorage withForge6Charge(ItemStack stack, ItemDataKey<Integer> key, int capacity) {
        return new NbtEnergyStorage(stack, key, capacity, capacity, capacity, true);
    }

    private NbtEnergyStorage(ItemStack stack, ItemDataKey<Integer> key, int capacity, int maxReceive,
                             int maxExtract, boolean readForge6Charge) {
        if (capacity < 0 || maxReceive < 0 || maxExtract < 0) {
            throw new IllegalArgumentException("Energy capacity and transfer limits must be nonnegative");
        }
        this.stack = stack;
        this.key = key;
        this.capacity = capacity;
        this.maxReceive = maxReceive;
        this.maxExtract = maxExtract;
        this.readForge6Charge = readForge6Charge;
    }

    @Override
    public int receiveEnergy(int requested, boolean simulate) {
        if (requested <= 0 || !canReceive()) return 0;
        int stored = getEnergyStored();
        int received = Math.min(requested, Math.min(maxReceive, Math.max(0, capacity - stored)));
        if (!simulate && received > 0) storeEnergy(stored + received);
        return received;
    }

    @Override
    public int extractEnergy(int requested, boolean simulate) {
        if (requested <= 0 || !canExtract()) return 0;
        int stored = getEnergyStored();
        int extracted = Math.min(requested, Math.min(maxExtract, stored));
        if (!simulate && extracted > 0) storeEnergy(stored - extracted);
        return extracted;
    }

    @Override
    public int getEnergyStored() {
        var current = key.get(stack);
        if (current != null) return Math.max(0, current);
        var legacy = readForge6Charge && !stack.isEmpty() ? stack.getTagElement(CoreNBTKeys.ENERGY) : null;
        if (legacy != null && legacy.contains(CoreNBTKeys.ENERGY_STORED, Tag.TAG_INT)) {
            return Math.max(0, legacy.getInt(CoreNBTKeys.ENERGY_STORED));
        }
        return 0;
    }

    private void storeEnergy(int amount) {
        key.set(stack, amount);
        // Reads and simulations leave the original stack intact. Retire the old balance only
        // after writing the authoritative value so draining to zero cannot revive it.
        var legacy = readForge6Charge ? stack.getTagElement(CoreNBTKeys.ENERGY) : null;
        if (legacy != null && legacy.contains(CoreNBTKeys.ENERGY_STORED, Tag.TAG_INT)) {
            legacy.remove(CoreNBTKeys.ENERGY_STORED);
            legacy.remove("MaxEnergyStored");
            legacy.remove("MaxEnergyUse");
            if (legacy.isEmpty()) stack.removeTagKey(CoreNBTKeys.ENERGY);
        }
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
