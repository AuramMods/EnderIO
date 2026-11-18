package com.enderio.enderio.content.machines.capacitor_bank;

import com.enderio.core.common.util.LongMathUtil;
import net.minecraft.core.Direction;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public record CapacitorBankEnergyStorage(CapacitorBankBlockEntity capacitorBank, @Nullable Direction side) implements IEnergyStorage {

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (!canReceive() || maxReceive <= 0) {
            return 0;
        }

        return capacitorBank.getNetwork().receiveEnergy(capacitorBank.getBlockPos(), side, maxReceive, simulate);
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (!canExtract() || maxExtract <= 0) {
            return 0;
        }

        return capacitorBank.getNetwork().extractEnergy(capacitorBank.getBlockPos(), side, maxExtract, simulate);
    }

    @Override
    public int getEnergyStored() {
        return capacitorBank.hasActiveNode() ? LongMathUtil.nonNegativeInt(capacitorBank.getNetwork().getTotalEnergyStored()) : 0;
    }

    @Override
    public int getMaxEnergyStored() {
        return capacitorBank.hasActiveNode() ? LongMathUtil.nonNegativeInt(capacitorBank.getNetwork().getTotalMaxEnergyStored()) : 0;
    }

    @Override
    public boolean canExtract() {
        return capacitorBank.hasActiveNode() && (side == null || capacitorBank.getIOMode(side).canOutput());
    }

    @Override
    public boolean canReceive() {
        return capacitorBank.hasActiveNode() && (side == null || capacitorBank.getIOMode(side).canInput());
    }

    @Nullable
    public static IEnergyStorage getSided(CapacitorBankBlockEntity capacitorBank, @Nullable Direction side) {
        if (!capacitorBank.hasActiveNode()) {
            return null;
        }
        if (side != null) {
            if (!capacitorBank.getIOMode(side).canInput() && !capacitorBank.getIOMode(side).canOutput()) {
                return null;
            }

            if (!capacitorBank.getIOMode(side).canConnect()) {
                return null;
            }
        }

        return new CapacitorBankEnergyStorage(capacitorBank, side);
    }
}
