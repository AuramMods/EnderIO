package com.enderio.core.common.recipes;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.Optional;

public final class RecipeLookup {
    private RecipeLookup() {}

    /** A saved ID can be removed or reused for a different recipe type by a datapack. */
    @SuppressWarnings("unchecked")
    public static <R extends Recipe<?>> Optional<R> find(RecipeManager manager, RecipeType<R> type, String id) {
        var location = ResourceLocation.tryParse(id);
        if (location == null) {
            return Optional.empty();
        }
        return manager.byKey(location).filter(recipe -> recipe.getType() == type).map(recipe -> (R) recipe);
    }
}
