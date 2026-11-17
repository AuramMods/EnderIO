package com.enderio.enderio.content.machines.slicer;

import com.enderio.core.common.recipes.RecipeCache;
import com.enderio.core.common.recipes.RecipeRegistry;
import com.enderio.enderio.api.EnderIOAPI;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.List;

public final class SlicerRecipeManager {
    private static final RecipeCache<SlicingRecipe.Input, SlicingRecipe, List<SlicingRecipe>> RECIPES =
        new RecipeCache<>(() -> RecipeRegistry.type(EnderIOAPI.rl("slicing")), List::copyOf);

    public static boolean isSlicerValid(RecipeManager manager, ItemStack stack, int slot) {
        if (slot < 0 || slot >= 6 || stack.isEmpty()) {
            return false;
        }
        return RECIPES.get(manager).stream().anyMatch(recipe -> recipe.inputs().get(slot).test(stack));
    }
}
