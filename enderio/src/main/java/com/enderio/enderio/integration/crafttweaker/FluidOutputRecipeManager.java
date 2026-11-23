package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.action.recipe.ActionRemoveRecipe;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.fluid.CTFluidIngredient;
import com.blamejared.crafttweaker.api.fluid.IFluidStack;
import com.enderio.core.common.recipes.OutputStack;
import net.minecraft.world.item.crafting.Recipe;
import org.openzen.zencode.java.ZenCodeType;

import java.util.List;

@ZenRegister
@ZenCodeType.Name("mods.enderio.FluidOutputRecipeManager")
public abstract class FluidOutputRecipeManager<T extends Recipe<?>> extends ProcessingRecipeManager<T> {
    @ZenCodeType.Method
    public List<T> getRecipesByFluidOutput(CTFluidIngredient output) {
        return getAllRecipes().stream().filter(recipe -> matchesFluidOutput(recipe, output)).toList();
    }

    @ZenCodeType.Method
    public void removeFluidOutput(CTFluidIngredient output) {
        CraftTweakerAPI.apply(new ActionRemoveRecipe<>(this, recipe -> matchesFluidOutput(recipe, output))
                .describeDefaultRemoval(output));
    }

    private boolean matchesFluidOutput(T recipe, CTFluidIngredient output) {
        return outputs(recipe).stream().map(OutputStack::getFluid)
                .anyMatch(stack -> !stack.isEmpty() && output.matches(IFluidStack.of(stack)));
    }
}
