package com.enderio.enderio.integration.gamestages;

import com.blamejared.recipestages.RecipeStagesUtil;
import com.blamejared.recipestages.recipes.IStagedRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.jetbrains.annotations.Nullable;

final class RecipeStagesAccess {
    private RecipeStagesAccess() {}

    @Nullable
    static CraftingRecipe resolve(@Nullable ServerPlayer owner, CraftingRecipe recipe) {
        if (!(recipe instanceof IStagedRecipe staged)) {
            return recipe;
        }
        if (owner == null || !RecipeStagesUtil.hasStageForRecipe(staged, owner)) {
            return null;
        }
        return staged.getRecipe();
    }
}
