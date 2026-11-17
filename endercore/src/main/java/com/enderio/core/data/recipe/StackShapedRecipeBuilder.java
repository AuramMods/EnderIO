package com.enderio.core.data.recipe;

import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/** Native shaped recipes with an output stack, including conduit variant NBT. */
public final class StackShapedRecipeBuilder extends ShapedRecipeBuilder {
    private final ItemStack result;

    private StackShapedRecipeBuilder(RecipeCategory category, ItemStack result) {
        super(category, result.getItem(), result.getCount());
        this.result = result.copy();
    }

    public static ShapedRecipeBuilder shaped(RecipeCategory category, ItemStack result) {
        return new StackShapedRecipeBuilder(category, result);
    }

    @Override
    public void save(Consumer<FinishedRecipe> output, ResourceLocation recipeId) {
        super.save(RecipeOutput.withResult(output, result), recipeId);
    }
}
