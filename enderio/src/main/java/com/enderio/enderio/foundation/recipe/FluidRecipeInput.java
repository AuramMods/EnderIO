package com.enderio.enderio.foundation.recipe;

import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraftforge.fluids.FluidStack;

public interface FluidRecipeInput extends RecipeInput {
    FluidStack getFluid(int slotIndex);
}
