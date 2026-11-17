package com.enderio.enderio.foundation.energy;

import com.enderio.enderio.api.io.IOConfigurable;
import com.enderio.core.common.item.ItemDataKey;
import com.enderio.enderio.api.EnderIOAPI;
import com.mojang.serialization.Codec;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import com.enderio.enderio.foundation.io.energy.IMachineEnergyStorage;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.IntTag;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public class PoweredMachineEnergyStorage implements IEnergyStorage, INBTSerializable<IntTag>, IMachineEnergyStorage {

    public static final ItemDataKey<Integer> ITEM_DATA = new ItemDataKey<>(EnderIOAPI.rl("energy"), Codec.intRange(0, Integer.MAX_VALUE), false);

    private final PoweredMachineBlockEntity machine;

    private int energyStored;

    public PoweredMachineEnergyStorage(PoweredMachineBlockEntity machine) {
        this.machine = machine;
    }

    @Override
    public int getEnergyStored() {
        return energyStored;
    }

    public void setEnergyStored(int energyStored) {
        int value = Math.max(0, energyStored);
        if (this.energyStored != value) {
            this.energyStored = value;
            machine.setChanged();
        }
    }

    @Override
    public int getMaxEnergyStored() {
        return machine.getMaxEnergyStored();
    }

    public final int addEnergy(int energyToAdd) {
        return addEnergy(energyToAdd, false);
    }

    public final int addEnergy(int energyToAdd, boolean simulate) {
        // Preserve charge loaded before capacity is known, or retained after a capacitor downgrade.
        int accepted = Math.min(Math.max(0, energyToAdd), Math.max(0, getMaxEnergyStored() - energyStored));
        if (!simulate && accepted > 0) {
            setEnergyStored(energyStored + accepted);
        }
        return accepted;
    }

    public final int consumeEnergy(int energyToConsume) {
        return consumeEnergy(energyToConsume, false);
    }

    public final int consumeEnergy(int energyToConsume, boolean simulate) {
        // Capped consumption rate
        energyToConsume = Math.min(Math.max(0, energyToConsume), getMaxEnergyUse());

        int energyExtracted = Math.min(energyStored, energyToConsume);

        if (!simulate && energyExtracted > 0) {
            setEnergyStored(energyStored - energyExtracted);
        }

        return energyExtracted;
    }

    @Nullable
    public IEnergyStorage getSided(Direction side) {
        if (!machine.energyIOMode().canInput() && !machine.energyIOMode().canOutput()) {
            return null;
        }

        if (!machine.getIOMode(side).canConnect()) {
            return null;
        }

        return new SidedAccess(this, side);
    }

    // region IMachineEnergyStorage Implementation (Legacy Interop)

    @Override
    public int takeEnergy(int energy) {
        // Internal use is independent of whether external consumers may extract energy.
        int taken = Math.min(Math.max(0, energy), energyStored);
        if (taken > 0) setEnergyStored(energyStored - taken);
        return taken;
    }

    @Override
    public int getMaxEnergyUse() {
        return Math.max(0, machine.getMaxEnergyUse());
    }

    @Override
    public IOConfigurable getConfig() {
        return machine;
    }

    @Override
    public EnergyIOMode getIOMode() {
        return machine.energyIOMode();
    }

    // endregion

    // region Directionless Access

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (!canReceive() || maxReceive <= 0) {
            return 0;
        }

        return addEnergy(maxReceive, simulate);
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (!canExtract() || maxExtract <= 0) {
            return 0;
        }

        int energyExtracted = Math.min(energyStored, maxExtract);

        if (!simulate && energyExtracted > 0) {
            setEnergyStored(energyStored - energyExtracted);
        }

        return energyExtracted;
    }

    @Override
    public boolean canExtract() {
        return !machine.isRemoved() && machine.energyIOMode().canOutput();
    }

    @Override
    public boolean canReceive() {
        return !machine.isRemoved() && machine.energyIOMode().canInput();
    }

    @Override
    public IntTag serializeNBT() {
        return IntTag.valueOf(energyStored);
    }

    @Override
    public void deserializeNBT(IntTag intTag) {
        energyStored = Math.max(0, intTag.getAsInt());
    }

    public IntTag serializeNBT(HolderLookup.Provider registries) { return serializeNBT(); }
    public void deserializeNBT(HolderLookup.Provider registries, IntTag tag) { deserializeNBT(tag); }

    // endregion

    public record SidedAccess(PoweredMachineEnergyStorage wrapped, Direction side) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (!canReceive() || maxReceive <= 0) {
                return 0;
            }

            return wrapped.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (!canExtract() || maxExtract <= 0) {
                return 0;
            }

            return wrapped.extractEnergy(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            return wrapped.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return wrapped.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return wrapped.canExtract() && (side == null || wrapped.machine.getIOMode(side).canConnect()
                && (!wrapped.machine.energyIOMode().respectIOConfig() || wrapped.machine.getIOMode(side).canOutput()));
        }

        @Override
        public boolean canReceive() {
            return wrapped.canReceive() && (side == null || wrapped.machine.getIOMode(side).canConnect()
                && (!wrapped.machine.energyIOMode().respectIOConfig() || wrapped.machine.getIOMode(side).canInput()));
        }
    }
}
