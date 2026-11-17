package com.enderio.core.common.capability;

import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.FluidStack;

public class EnderFluidUtil {
    /** Copies native fluid NBT without reviving an empty stack. */
    public static FluidStack copyWithAmount(FluidStack fluid, int amount) {
        return fluid.isEmpty() || amount <= 0 ? FluidStack.EMPTY : new FluidStack(fluid, amount);
    }

    public static boolean isEmpty(IFluidHandler fluidHandler) {
        for (int i = 0; i < fluidHandler.getTanks(); i++) {
            if (!fluidHandler.getFluidInTank(i).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public static boolean isFull(IFluidHandler fluidHandler) {
        for (int i = 0; i < fluidHandler.getTanks(); i++) {
            if (fluidHandler.getFluidInTank(i).getAmount() < fluidHandler.getTankCapacity(i)) {
                return false;
            }
        }

        return true;
    }
}
