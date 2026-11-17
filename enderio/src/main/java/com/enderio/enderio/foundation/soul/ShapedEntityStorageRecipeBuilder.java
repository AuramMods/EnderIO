package com.enderio.enderio.foundation.soul;

import com.enderio.enderio.init.EIORecipes;
import com.google.gson.JsonObject;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class ShapedEntityStorageRecipeBuilder extends ShapedRecipeBuilder {
    public ShapedEntityStorageRecipeBuilder(RecipeCategory category, ItemLike result, int count) {
        super(category, result, count);
    }

    public static ShapedEntityStorageRecipeBuilder shaped(RecipeCategory category, ItemLike result) {
        return shaped(category, result, 1);
    }

    public static ShapedEntityStorageRecipeBuilder shaped(RecipeCategory category, ItemLike result, int count) {
        return new ShapedEntityStorageRecipeBuilder(category, result, count);
    }

    @Override
    public void save(Consumer<FinishedRecipe> output, ResourceLocation recipeId) {
        super.save(recipe -> output.accept(new FinishedRecipe() {
            @Override public void serializeRecipeData(JsonObject json) { recipe.serializeRecipeData(json); }
            @Override public ResourceLocation getId() { return recipe.getId(); }
            @Override public RecipeSerializer<?> getType() { return EIORecipes.SHAPED_ENTITY_STORAGE.get(); }
            @Nullable @Override public JsonObject serializeAdvancement() { return recipe.serializeAdvancement(); }
            @Nullable @Override public ResourceLocation getAdvancementId() { return recipe.getAdvancementId(); }
        }), recipeId);
    }
}
