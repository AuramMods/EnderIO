package com.enderio.enderio.compat.laserio;

import com.direwolf20.laserio.common.items.filters.BaseFilter;
import com.direwolf20.laserio.common.items.filters.FilterBasic;
import com.enderio.enderio.api.filter.FluidFilter;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public class LaserFluidFilter implements FluidFilter {

    private final java.util.function.Supplier<ItemStack> container;

    public LaserFluidFilter(ItemStack container) {
        this(() -> container);
    }

    public LaserFluidFilter(java.util.function.Supplier<ItemStack> container) {
        this.container = container;
    }

    @Override
    public FluidStack test(@Nullable IFluidHandler target, FluidStack stack) {
        ItemStack container = this.container.get();
        if (container.isEmpty()) {
            return stack;
        }
        final boolean isAllowList = BaseFilter.getAllowList(container);
        final boolean shouldCompareNbt = BaseFilter.getCompareNBT(container);

        var filterSlotHandler = FilterBasic.getInventory(container);
        for (int i = 0; i < filterSlotHandler.getSlots(); ++i) {
            var handler = FluidUtil.getFluidHandler(filterSlotHandler.getStackInSlot(i)).orElse(null);
            if (handler == null) {
                continue;
            }
            for (int tank = 0; tank < handler.getTanks(); ++tank) {
                var match = handler.getFluidInTank(tank);
                if (!match.isEmpty() && (shouldCompareNbt ? match.isFluidEqual(stack) : match.getFluid() == stack.getFluid())) {
                    return isAllowList ? stack : FluidStack.EMPTY;
                }
            }
        }

        return isAllowList ? FluidStack.EMPTY : stack;
    }
}
