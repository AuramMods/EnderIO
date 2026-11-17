package com.enderio.enderio.content.machines.alloy;

import com.enderio.core.common.recipes.SizedIngredient;
import com.enderio.enderio.api.EnderIOAPI;
import com.mojang.logging.LogUtils;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/** Adds alloy-smelter equivalents after Forge has evaluated recipe conditions. */
public final class SmeltingRecipeInheritance {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation inheritedId(ResourceLocation original) {
        return EnderIOAPI.rl("smelting/" + original.getNamespace() + "/" + original.getPath());
    }

    public static void inherit(RecipeManager manager, int energyPerItem) {
        var recipes = new ArrayList<>(manager.getRecipes());
        boolean changed = false;
        for (var smelting : manager.getAllRecipesFor(RecipeType.SMELTING)) {
            var id = inheritedId(smelting.getId());
            // An explicitly supplied datapack recipe owns its ID.
            if (manager.byKey(id).isPresent()) {
                continue;
            }
            var result = smelting.getResultItem(RegistryAccess.EMPTY);
            var ingredients = smelting.getIngredients();
            if (result.isEmpty() || ingredients.size() != 1 || ingredients.get(0).isEmpty()) {
                LOGGER.warn("[EnderIO] Cannot inherit smelting recipe {}: empty result or invalid ingredient", smelting.getId());
                continue;
            }
            recipes.add(new AlloySmeltingRecipe(List.of(new SizedIngredient(ingredients.get(0), 1)),
                result.copy(), energyPerItem, smelting.getExperience(), true).withId(id));
            changed = true;
        }
        if (changed) {
            manager.replaceRecipes(recipes);
        }
    }
}
