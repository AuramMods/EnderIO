package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.recipe.component.IDecomposedRecipe;
import com.blamejared.crafttweaker.api.recipe.handler.IRecipeHandler;
import com.blamejared.crafttweaker.api.recipe.manager.base.IRecipeManager;
import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
import com.enderio.enderio.content.machines.alloy.PrimitiveAlloySmeltingRecipe;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import java.util.Optional;

@IRecipeHandler.For(AlloySmeltingRecipe.class)
@IRecipeHandler.For(PrimitiveAlloySmeltingRecipe.class)
public final class AlloyRecipeHandler implements IRecipeHandler<AlloySmeltingRecipe> {
    @Override
    public String dumpToCommandString(IRecipeManager<? super AlloySmeltingRecipe> manager, AlloySmeltingRecipe recipe) {
        var json = AlloySmeltingRecipe.Serializer.CODEC.codec().encodeStart(JsonOps.INSTANCE, recipe)
                .result().orElseThrow();
        return manager.getCommandString() + ".addRecipeJson(\"%s\", %s);".formatted(recipe.getId().getPath(),
                new JsonPrimitive(json.toString()));
    }

    @Override
    public <U extends Recipe<?>> boolean doesConflict(IRecipeManager<? super AlloySmeltingRecipe> manager,
            AlloySmeltingRecipe first, U second) {
        return first.getId().equals(second.getId());
    }

    @Override
    public Optional<IDecomposedRecipe> decompose(IRecipeManager<? super AlloySmeltingRecipe> manager,
            AlloySmeltingRecipe recipe) {
        return Optional.empty();
    }

    @Override
    public Optional<AlloySmeltingRecipe> recompose(IRecipeManager<? super AlloySmeltingRecipe> manager,
            ResourceLocation id, IDecomposedRecipe recipe) {
        return Optional.empty();
    }
}
