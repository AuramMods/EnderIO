package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.enderio.enderio.content.machines.slicer.SlicingRecipe;
import com.enderio.enderio.init.EIORecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import org.openzen.zencode.java.ZenCodeType;

import java.util.Arrays;

@ZenRegister
@ZenCodeType.Name("mods.enderio.SlicingManager")
public final class SlicingManager extends ProcessingRecipeManager<SlicingRecipe> {
    @Override
    public RecipeType<SlicingRecipe> getRecipeType() {
        return EIORecipes.SLICING.type().get();
    }

    @ZenCodeType.Method
    public void addRecipe(String name, IItemStack output, int energy, IIngredient... inputs) {
        add(create(scriptId(name), output, energy, inputs));
    }

    @ZenCodeType.Method
    public void replaceRecipe(String id, IItemStack output, int energy, IIngredient... inputs) {
        replace(create(new ResourceLocation(id), output, energy, inputs));
    }

    private static SlicingRecipe create(ResourceLocation id, IItemStack output, int energy, IIngredient[] inputs) {
        if (inputs.length != 6) {
            throw new IllegalArgumentException("Slice'N'Splice needs exactly six positioned ingredients");
        }
        return new SlicingRecipe(outputStack(output), Arrays.stream(inputs).map(IIngredient::asVanillaIngredient).toList(),
                positive(energy, "Energy"), id);
    }
}
