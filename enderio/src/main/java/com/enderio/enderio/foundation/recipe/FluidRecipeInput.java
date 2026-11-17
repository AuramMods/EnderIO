package com.enderio.enderio.foundation.recipe;

import com.enderio.core.common.recipes.RecipeInput;
import net.minecraftforge.fluids.FluidStack;

public interface FluidRecipeInput extends RecipeInput {
    FluidStack getFluid(int slotIndex);
}
