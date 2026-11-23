package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.recipe.component.IDecomposedRecipe;
import com.blamejared.crafttweaker.api.recipe.handler.IRecipeHandler;
import com.blamejared.crafttweaker.api.recipe.manager.base.IRecipeManager;
import com.enderio.core.common.recipes.CodecRecipeSerializer;
import com.enderio.enderio.content.enchanter.EnchanterRecipe;
import com.enderio.enderio.content.fire_crafting.FireCraftingRecipe;
import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
import com.enderio.enderio.content.machines.alloy.PrimitiveAlloySmeltingRecipe;
import com.enderio.enderio.content.machines.obelisks.weather.WeatherChangeRecipe;
import com.enderio.enderio.content.machines.painting.PaintingRecipe;
import com.enderio.enderio.content.machines.sag_mill.Forge6GrindingBallRecipe;
import com.enderio.enderio.content.machines.sag_mill.SagMillingRecipe;
import com.enderio.enderio.content.machines.slicer.SlicingRecipe;
import com.enderio.enderio.content.machines.soul_binder.SoulBindingRecipe;
import com.enderio.enderio.content.machines.vat.FermentingRecipe;
import com.enderio.enderio.content.storage.fluid_tank.TankRecipe;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import java.util.Optional;

@IRecipeHandler.For(AlloySmeltingRecipe.class)
@IRecipeHandler.For(PrimitiveAlloySmeltingRecipe.class)
@IRecipeHandler.For(SagMillingRecipe.class)
@IRecipeHandler.For(SlicingRecipe.class)
@IRecipeHandler.For(FermentingRecipe.class)
@IRecipeHandler.For(TankRecipe.class)
@IRecipeHandler.For(WeatherChangeRecipe.class)
@IRecipeHandler.For(PaintingRecipe.class)
@IRecipeHandler.For(EnchanterRecipe.class)
@IRecipeHandler.For(Forge6GrindingBallRecipe.class)
@IRecipeHandler.For(FireCraftingRecipe.class)
@IRecipeHandler.For(SoulBindingRecipe.class)
public final class ProcessingRecipeHandler implements IRecipeHandler<Recipe<?>> {
    @Override
    public String dumpToCommandString(IRecipeManager<? super Recipe<?>> manager, Recipe<?> recipe) {
        @SuppressWarnings("unchecked")
        var serializer = (CodecRecipeSerializer<Recipe<?>>) recipe.getSerializer();
        var json = serializer.codec().codec().encodeStart(JsonOps.INSTANCE, recipe)
                .result().orElseThrow();
        return manager.getCommandString() + ".addRecipeJson(\"%s\", %s);".formatted(recipe.getId().getPath(),
                new JsonPrimitive(json.toString()));
    }

    @Override
    public <U extends Recipe<?>> boolean doesConflict(IRecipeManager<? super Recipe<?>> manager, Recipe<?> first, U second) {
        return first.getId().equals(second.getId());
    }

    @Override
    public Optional<IDecomposedRecipe> decompose(IRecipeManager<? super Recipe<?>> manager, Recipe<?> recipe) {
        return Optional.empty();
    }

    @Override
    public Optional<Recipe<?>> recompose(IRecipeManager<? super Recipe<?>> manager,
            ResourceLocation id, IDecomposedRecipe recipe) {
        return Optional.empty();
    }
}
