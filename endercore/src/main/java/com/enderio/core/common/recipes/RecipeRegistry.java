package com.enderio.core.common.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import java.util.Objects;

/** Looks up already registered recipe services without initializing the owning mod's registration class. */
public final class RecipeRegistry {
    private RecipeRegistry() {}
    @SuppressWarnings("unchecked")
    public static <T extends Recipe<?>> RecipeSerializer<T> serializer(ResourceLocation id) {
        return (RecipeSerializer<T>) Objects.requireNonNull(BuiltInRegistries.RECIPE_SERIALIZER.get(id), "Missing recipe serializer: " + id);
    }
    @SuppressWarnings("unchecked")
    public static <T extends Recipe<?>> RecipeType<T> type(ResourceLocation id) {
        return (RecipeType<T>) Objects.requireNonNull(BuiltInRegistries.RECIPE_TYPE.get(id), "Missing recipe type: " + id);
    }
}
