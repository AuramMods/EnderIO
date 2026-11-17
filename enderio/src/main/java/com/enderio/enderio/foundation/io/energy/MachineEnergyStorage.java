package com.enderio.enderio.foundation.io.energy;

import com.enderio.enderio.api.io.IOConfigurable;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.foundation.MachineNBTKeys;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Machine energy storage medium.
 * Uses capacitor keys to determine maximum capacity and transfer rate.
 * Also provides sided access through capabilities.
 */
public class MachineEnergyStorage implements IMachineEnergyStorage, INBTSerializable<CompoundTag> {
    private final IOConfigurable config;
    private final EnergyIOMode ioMode;

    private int energyStored;

    private final Supplier<Integer> capacity;
    private final Supplier<Integer> usageRate;

    public MachineEnergyStorage(IOConfigurable config, EnergyIOMode ioMode, Supplier<Integer> capacity, Supplier<Integer> usageRate) {
        this.config = config;
        this.ioMode = ioMode;
        this.capacity = capacity;
        this.usageRate = usageRate;
    }

    @Override
    public final IOConfigurable getConfig() {
        return config;
    }

    @Override
    public final EnergyIOMode getIOMode() {
        return ioMode;
    }

    // Override in BE
    protected void onContentsChanged() {

    }

    @Override
    public int getEnergyStored() {
        return Math.min(energyStored, getMaxEnergyStored());
    }

    /**
     * Set the energy stored in the storage.
     */
    public void setEnergyStored(int energy) {
        int value = Math.max(0, energy);
        if (energyStored != value) {
            energyStored = value;
            onContentsChanged();
        }
    }

    @Override
    public int addEnergy(int energy) {
        return addEnergy(energy, false);
    }

    @Override
    public int addEnergy(int energy, boolean simulate) {
        int accepted = Math.min(Math.max(0, energy), Math.max(0, getMaxEnergyStored() - energyStored));
        if (!simulate && accepted > 0) {
            setEnergyStored(energyStored + accepted);
        }
        return accepted;
    }

    @Override
    public int takeEnergy(int energy) {
        int taken = Math.min(Math.max(0, energy), getEnergyStored());
        if (taken > 0) {
            setEnergyStored(energyStored - taken);
        }
        return taken;
    }

    @Override
    public int consumeEnergy(int energy, boolean simulate) {
        // Cap rate
        int usableEnergy = Math.min(getEnergyStored(), Math.min(Math.max(0, energy), getMaxEnergyUse()));
        if (!simulate) {
            return takeEnergy(usableEnergy);
        }
        return usableEnergy;
    }

    @Override
    public int getMaxEnergyStored() {
        return Math.max(0, capacity.get());
    }

    @Override
    public int getMaxEnergyUse() {
        return Math.max(0, usageRate.get());
    }

    @Override
    public boolean canExtract() {
        return ioMode.canOutput();
    }

    @Override
    public boolean canReceive() {
        return ioMode.canInput();
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (!canReceive() || getMaxEnergyStored() == 0) {
            return 0;
        }

        return addEnergy(maxReceive, simulate);
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (!canExtract()) {
            return 0;
        }

        int energyExtracted = Math.min(getEnergyStored(), Math.max(0, maxExtract));
        if (!simulate) {
            takeEnergy(energyExtracted);
        }

        return energyExtracted;
    }

    @Nullable
    public IEnergyStorage getForSide(@Nullable Direction side) {
        if (side == null) {
            return this;
        }

        if (config.getIOMode(side).canConnect()) {
            return new Sided(this, side);
        }

        return null;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        // Capacitor capacity may not be initialized yet; retain the actual stored charge.
        tag.putInt(MachineNBTKeys.ENERGY_STORED, energyStored);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        energyStored = Math.max(0, nbt.getInt(MachineNBTKeys.ENERGY_STORED));
    }

    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        return serializeNBT();
    }

    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        deserializeNBT(tag);
    }

    private record Sided(MachineEnergyStorage wrapped, Direction side) implements IEnergyStorage {

        @Override
        public int getEnergyStored() {
            return wrapped.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return wrapped.getMaxEnergyStored();
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (!canReceive()) {
                return 0;
            }

            return wrapped.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (!canExtract()) {
                return 0;
            }

            return wrapped.extractEnergy(maxExtract, simulate);
        }

        @Override
        public boolean canExtract() {
            if (!wrapped.getConfig().getIOMode(side).canConnect()) {
                return false;
            }
            if (wrapped.getIOMode().respectIOConfig() && !wrapped.getConfig().getIOMode(side).canOutput()) {
                return false;
            }

            return wrapped.canExtract();
        }

        @Override
        public boolean canReceive() {
            if (!wrapped.getConfig().getIOMode(side).canConnect()) {
                return false;
            }
            if (wrapped.getIOMode().respectIOConfig() && !wrapped.getConfig().getIOMode(side).canInput()) {
                return false;
            }

            return wrapped.canReceive();
        }
    }
}
