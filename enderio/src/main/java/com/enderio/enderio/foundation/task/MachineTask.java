package com.enderio.enderio.foundation.task;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

public interface MachineTask extends INBTSerializable<CompoundTag> {
    // Source compatibility for callers that already have a registry context.
    default CompoundTag serializeNBT(net.minecraft.core.HolderLookup.Provider registries) {
        return serializeNBT();
    }

    default void deserializeNBT(net.minecraft.core.HolderLookup.Provider registries, CompoundTag tag) {
        deserializeNBT(tag);
    }

    void tick();

    float getProgress();

    boolean isCompleted();
}
