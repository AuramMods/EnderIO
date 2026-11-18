package com.enderio.modded_conduits.data;

import com.enderio.core.data.recipe.EnderRecipeProvider;
import net.minecraft.data.DataProvider;
import net.minecraft.data.CachedOutput;
import com.enderio.core.data.recipe.RecipeOutput;
import com.enderio.core.data.recipe.SubRecipeProvider;
import com.enderio.modded_conduits.common.ModdedConduits;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import java.util.concurrent.CompletableFuture;

public class ModConduitRecipeProvider implements DataProvider {
    private final EnderRecipeProvider delegate;
    public ModConduitRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        delegate = new EnderRecipeProvider(output, registries) {{
        addProvider(new SubRecipeProvider() {
            @Override public void buildRecipes(RecipeOutput output, HolderLookup.Provider lookup) {
                ModdedConduits.executeOnLoadedModules(module -> module.buildRecipes(lookup, output));
            }
        });
        }};
    }

    @Override public CompletableFuture<?> run(CachedOutput output) { return delegate.run(output); }

    @Override public String getName() { return "Modded Conduit Recipes"; }
}
