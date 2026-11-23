package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.ingredient.IIngredientWithAmount;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
import net.minecraft.resources.ResourceLocation;
import org.openzen.zencode.java.ZenCodeType;

import java.util.Arrays;

@ZenRegister
@ZenCodeType.Name("mods.enderio.AlloyRecipeManager")
public abstract class AlloyRecipeManager extends ProcessingRecipeManager<AlloySmeltingRecipe> {
    @ZenCodeType.Method
    public void addRecipe(String name, IItemStack output, int energy, float experience, IIngredientWithAmount... inputs) {
        add(create(scriptId(name), output, energy, experience, inputs));
    }

    @ZenCodeType.Method
    public void replaceRecipe(String id, IItemStack output, int energy, float experience, IIngredientWithAmount... inputs) {
        replace(create(new ResourceLocation(id), output, energy, experience, inputs));
    }

    protected AlloySmeltingRecipe finish(AlloySmeltingRecipe recipe, ResourceLocation id) {
        return recipe.withId(id);
    }

    private AlloySmeltingRecipe create(ResourceLocation id, IItemStack output, int energy, float experience,
            IIngredientWithAmount[] inputs) {
        if (inputs.length < 1 || inputs.length > 3) {
            throw new IllegalArgumentException("Alloy smelting needs one to three inputs");
        }
        if (!Float.isFinite(experience) || experience < 0) {
            throw new IllegalArgumentException("Alloy smelting XP must be finite and nonnegative");
        }
        var sized = Arrays.stream(inputs).map(ProcessingRecipeManager::sized).toList();
        return finish(new AlloySmeltingRecipe(sized, outputStack(output), positive(energy, "Energy"), experience), id);
    }
}
