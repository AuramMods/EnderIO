package com.enderio.enderio.foundation.recipe;

import com.enderio.core.common.recipes.RecipeCache;
import com.enderio.core.common.recipes.RecipeIngredientIndex;
import com.enderio.core.common.recipes.RecipeInput;
import com.enderio.enderio.foundation.inventory.MachineInventory;
import com.enderio.enderio.foundation.inventory.MultiSlotAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class RecipeInputCache<T extends RecipeInput, R extends Recipe<T>> {
    private final RecipeCache<T, R, RecipeIngredientIndex> cache;

    public RecipeInputCache(Supplier<RecipeType<R>> recipeType) {
        this(recipeType, recipe -> true);
    }

    public RecipeInputCache(Supplier<RecipeType<R>> recipeType, Predicate<R> filter) {
        cache = new RecipeCache<>(recipeType, recipes -> new RecipeIngredientIndex(
            recipes.stream().filter(filter).map(Recipe::getIngredients).toList()));
    }

    /**
     * Test if there is a valid recipe if toAdd was added to the inventory.
     */
    public boolean hasValidRecipeIf(RecipeManager manager, MachineInventory inventory, MultiSlotAccess inputs, int slot, ItemStack toAdd) {
        // Collect the list of items that the recipe will match against
        var currentItems = new ArrayList<ItemStack>();

        // Build the new inventory state after the addition
        for (int i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i);
            var invStack = input.getItemStack(inventory);
            if (input.getIndex() == slot) {
                currentItems.add(toAdd);
            } else if (!invStack.isEmpty()) {
                currentItems.add(invStack);
            }
        }

        // Try and match the items list
        return hasRecipe(manager, currentItems);
    }

    public boolean hasRecipe(RecipeManager manager, List<ItemStack> inputs) {
        return cache.get(manager).hasRecipe(inputs);
    }
}
