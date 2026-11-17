package com.enderio.enderio.foundation.recipe;

import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
import com.enderio.enderio.content.machines.painting.PaintingRecipe;
import com.enderio.enderio.content.machines.sag_mill.SagMillingRecipe;
import com.enderio.enderio.content.machines.soul_binder.SoulBindingRecipe;
import com.enderio.enderio.content.machines.vat.FermentingRecipe;
import com.enderio.enderio.init.EIORecipes;

public class MachineRecipeCaches {
    public static final RecipeInputCache<AlloySmeltingRecipe.Input, AlloySmeltingRecipe> ALLOY_SMELTING_ONLY_ALLOY = new RecipeInputCache<>(
            EIORecipes.ALLOY_SMELTING.type(), recipe -> !recipe.isSmelting());

    public static final RecipeInputCache<AlloySmeltingRecipe.Input, AlloySmeltingRecipe> ALLOY_SMELTING_ONLY_SMELTING = new RecipeInputCache<>(
            EIORecipes.ALLOY_SMELTING.type(), AlloySmeltingRecipe::isSmelting);

    public static final RecipeInputCache<PaintingRecipe.Input, PaintingRecipe> PAINTING = new RecipeInputCache<>(
            EIORecipes.PAINTING.type());

    public static final RecipeInputCache<SagMillingRecipe.Input, SagMillingRecipe> SAG_MILLING = new RecipeInputCache<>(
            EIORecipes.SAG_MILLING.type());

    public static final RecipeInputCache<SoulBindingRecipe.Input, SoulBindingRecipe> SOUL_BINDING = new RecipeInputCache<>(
            EIORecipes.SOUL_BINDING.type());

    public static final RecipeInputCache<FermentingRecipe.Input, FermentingRecipe> FERMENTING = new RecipeInputCache<>(
            EIORecipes.VAT_FERMENTING.type());

}
