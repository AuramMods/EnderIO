package com.enderio.modded_conduits.common.modules.mekanism.chemical_filter;

import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalResource;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalHandlerAdapter;
import org.jetbrains.annotations.Nullable;

public interface ChemicalFilter {
    /**
     * Test whether the chemical stack passes this filter.
     * @param target The target handler which can be used for comparisons using the destination before moving.
     * @param stack The stack being tested.
     * @return The stack that is permitted (can differ in amount), use {@link ChemicalResource#EMPTY} to transfer nothing.
     */
    ChemicalResource test(@Nullable ChemicalHandlerAdapter target, ChemicalResource stack);
}
