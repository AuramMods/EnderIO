package com.enderio.enderio.foundation.attachment;

import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.io.fluid.MachineFluidHandler;
import com.enderio.enderio.foundation.io.fluid.MachineTankLayout;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.function.BiFunction;
import net.minecraftforge.fluids.capability.IFluidHandler;

public interface FluidTankUser {

    MachineTankLayout getTankLayout();

    MachineFluidHandler getFluidHandler();

    MachineFluidHandler createFluidHandler();

    default void saveTank(HolderLookup.Provider lookupProvider, CompoundTag tag) {
        tag.put(MachineNBTKeys.FLUIDS, getFluidHandler().serializeNBT(lookupProvider));
    }

    default void loadTank(HolderLookup.Provider lookupProvider, CompoundTag tag) {
        // Forge 1.20.1 Ender IO 6.x used a singular key for the machine tank handler.
        String key = tag.contains(MachineNBTKeys.FLUIDS) ? MachineNBTKeys.FLUIDS : "Fluid";
        getFluidHandler().deserializeNBT(lookupProvider, tag.getCompound(key));
    }

    BiFunction<BlockEntity, Direction, IFluidHandler> FLUID_HANDLER_PROVIDER = (be, side) -> {
        if (be instanceof FluidTankUser user) {
            return user.getFluidHandler().getForSide(side);
        }
        return null;
    };
}
