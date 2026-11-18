package com.enderio.modded_conduits.common.modules.mekanism.laserio;

import com.direwolf20.laserio.common.containers.FilterBasicContainer;
import com.direwolf20.laserio.common.containers.customhandler.FilterBasicHandler;
import com.direwolf20.laserio.common.items.filters.BaseFilter;
import com.enderio.modded_conduits.common.modules.mekanism.MekanismModule;
import com.enderio.modded_conduits.common.modules.mekanism.chemical_filter.ChemicalFilter;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalResource;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalHandlerAdapter;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class LaserChemicalFilter implements ChemicalFilter {

    private final java.util.function.Supplier<ItemStack> container;

    public LaserChemicalFilter(ItemStack container) {
        this(() -> container);
    }

    public LaserChemicalFilter(java.util.function.Supplier<ItemStack> container) {
        this.container = container;
    }

    @Override
    public ChemicalResource test(@Nullable ChemicalHandlerAdapter target, ChemicalResource stack) {
        var currentFilter = container.get();
        final boolean isAllowList = BaseFilter.getAllowList(currentFilter);

        FilterBasicHandler filterSlotHandler = new FilterBasicHandler(FilterBasicContainer.SLOTS, currentFilter);

        for (int i = 0; i < filterSlotHandler.getSlots(); ++i) {
            ItemStack itemStack = filterSlotHandler.getStackInSlot(i);

            if (!itemStack.isEmpty()) {
                ChemicalHandlerAdapter capability = ChemicalHandlerAdapter.find(capabilityType -> itemStack.getCapability(capabilityType).orElse(null));
                if (capability != null) {
                    for (int tank = 0; tank < capability.getChemicalTanks(); ++tank) {
                        ChemicalResource fluidStack = capability.getChemicalInTank(tank);
                        if (!fluidStack.isEmpty()) {
                            if (ChemicalResource.isSameChemical(fluidStack, stack)) {
                                return isAllowList ? stack : ChemicalResource.EMPTY;
                            }
                        }
                    }
                }
            }
        }

        return isAllowList ? ChemicalResource.EMPTY : stack;
    }
}
