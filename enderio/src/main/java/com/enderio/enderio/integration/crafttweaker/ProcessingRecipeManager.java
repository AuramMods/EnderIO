package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.action.recipe.ActionAddRecipe;
import com.blamejared.crafttweaker.api.action.recipe.ActionRemoveRecipe;
import com.blamejared.crafttweaker.api.action.recipe.ActionRemoveRecipeByName;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker.api.recipe.manager.base.IRecipeManager;
import com.enderio.core.common.recipes.OutputStack;
import com.enderio.enderio.foundation.MachineRecipe;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.openzen.zencode.java.ZenCodeType;

import java.util.List;

@ZenRegister
@ZenCodeType.Name("mods.enderio.ProcessingRecipeManager")
public abstract class ProcessingRecipeManager<T extends Recipe<?>> implements IRecipeManager<T> {
    @ZenCodeType.Method
    public void addRecipeJson(String name, String json) {
        add(decode(scriptId(name), json));
    }

    @ZenCodeType.Method
    public void replaceRecipeJson(String id, String json) {
        replace(decode(new ResourceLocation(id), json));
    }

    @Override
    public final List<T> getRecipesByOutput(IIngredient output) {
        return getAllRecipes().stream().filter(recipe -> matchesOutput(recipe, output)).toList();
    }

    @Override
    public final void remove(IIngredient output) {
        CraftTweakerAPI.apply(new ActionRemoveRecipe<>(this, recipe -> matchesOutput(recipe, output))
                .describeDefaultRemoval(output));
    }

    protected final ResourceLocation scriptId(String name) {
        return new ResourceLocation("crafttweaker", fixRecipeName(name));
    }

    protected final void add(T recipe) {
        CraftTweakerAPI.apply(new ActionAddRecipe<>(this, recipe).outputDescriber(
                value -> outputs(value).stream().map(ProcessingRecipeManager::displayOutput).toList().toString()));
    }

    protected final void replace(T recipe) {
        if (!getRecipes().containsKey(recipe.getId())) {
            throw new IllegalArgumentException("No " + getBracketResourceLocation() + " recipe named " + recipe.getId());
        }
        CraftTweakerAPI.apply(new ActionRemoveRecipeByName<>(this, recipe.getId()));
        add(recipe);
    }

    protected final List<OutputStack> outputs(T recipe) {
        return CraftTweakerAPI.getAccessibleElementsProvider().registryAccess(registry -> {
            if (recipe instanceof MachineRecipe<?> machine) {
                return machine.getResultStacks(registry);
            }
            return List.of(OutputStack.of(recipe.getResultItem(registry)));
        });
    }

    private boolean matchesOutput(T recipe, IIngredient output) {
        return outputs(recipe).stream().map(OutputStack::getItem)
                .anyMatch(stack -> !stack.isEmpty() && output.matches(IItemStack.of(stack)));
    }

    private static String displayOutput(OutputStack output) {
        if (!output.getItem().isEmpty()) {
            return IItemStack.of(output.getItem()).getCommandString();
        }
        if (!output.getFluid().isEmpty()) {
            var fluid = output.getFluid();
            return fluid.getAmount() + " mB " + BuiltInRegistries.FLUID.getKey(fluid.getFluid());
        }
        return "no item or fluid";
    }

    private T decode(ResourceLocation id, String json) {
        var object = JsonParser.parseString(json).getAsJsonObject();
        if (!object.has("type")) {
            object.addProperty("type", getBracketResourceLocation().toString());
        }
        var recipe = RecipeManager.fromJson(id, object);
        RecipeType<T> expected = getRecipeType();
        if (recipe.getType() != expected) {
            throw new IllegalArgumentException("Recipe " + id + " must use recipe type " + getBracketResourceLocation());
        }
        @SuppressWarnings("unchecked")
        T typed = (T) recipe;
        return typed;
    }
}
