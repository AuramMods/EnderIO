package com.enderio.core.data.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.CachedOutput;
import java.util.function.Consumer;
import net.minecraft.data.recipes.RecipeProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public abstract class EnderRecipeProvider extends RecipeProvider {

    private final CompletableFuture<HolderLookup.Provider> registries;
    private HolderLookup.Provider lookup;

    private final List<SubRecipeProvider> subRecipeProviders = new ArrayList<>();

    public EnderRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output);
        this.registries = registries;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        return registries.thenCompose(provider -> {
            lookup = provider;
            return super.run(output);
        });
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> recipeOutput) {
        for (var provider : subRecipeProviders) {
            provider.buildRecipes(recipeOutput, lookup);
        }
    }

    protected void addProvider(SubRecipeProvider provider) {
        subRecipeProviders.add(provider);
    }
}
