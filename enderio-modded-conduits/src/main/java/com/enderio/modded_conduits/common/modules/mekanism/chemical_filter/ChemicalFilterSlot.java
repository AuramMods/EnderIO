package com.enderio.modded_conduits.common.modules.mekanism.chemical_filter;

import com.enderio.enderio.content.filters.FilterSlot;
import com.enderio.modded_conduits.common.modules.mekanism.MekanismModule;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalResource;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalHandlerAdapter;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ChemicalFilterSlot extends FilterSlot<ChemicalResource> {

    public ChemicalFilterSlot(Supplier<ChemicalResource> getter, Consumer<ChemicalResource> setter, int slot, int x, int y) {
        super(getter, setter, slot, x, y);
    }

    @Override
    protected ChemicalResource emptyResource() {
        return ChemicalResource.EMPTY;
    }

    @Override
    public Optional<ChemicalResource> getResourceFrom(ItemStack itemStack) {
        ChemicalHandlerAdapter capability = ChemicalHandlerAdapter.find(capabilityType -> itemStack.getCapability(capabilityType).orElse(null));
        if (capability != null) {
            for (int tank = 0; tank < capability.getChemicalTanks(); tank++) {
                var ghost = capability.getChemicalInTank(tank);
                if (!ghost.isEmpty()) return Optional.of(ghost);
            }
        }

        return Optional.empty();
    }
}
