package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.enderio.enderio.content.machines.soul_binder.SoulBindingRecipe;
import com.enderio.enderio.init.EIORecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister
@ZenCodeType.Name("mods.enderio.SoulBindingManager")
public final class SoulBindingManager extends ProcessingRecipeManager<SoulBindingRecipe> {
    @Override
    public RecipeType<SoulBindingRecipe> getRecipeType() {
        return EIORecipes.SOUL_BINDING.type().get();
    }

    @ZenCodeType.Method
    public void addRecipe(String name, IIngredient input, IItemStack output, int energy, int xpLevels,
            SoulSelector selector, boolean copyInputNbt) {
        add(create(scriptId(name), input, output, energy, xpLevels, selector, copyInputNbt));
    }

    @ZenCodeType.Method
    public void replaceRecipe(String id, IIngredient input, IItemStack output, int energy, int xpLevels,
            SoulSelector selector, boolean copyInputNbt) {
        replace(create(new ResourceLocation(id), input, output, energy, xpLevels, selector, copyInputNbt));
    }

    private static SoulBindingRecipe create(ResourceLocation id, IIngredient input, IItemStack output, int energy,
            int xpLevels, SoulSelector selector, boolean copyInputNbt) {
        var ingredient = input.asVanillaIngredient();
        if (ingredient.isEmpty() || xpLevels < 0) {
            throw new IllegalArgumentException("Soul binding needs a nonempty input and nonnegative XP levels");
        }
        return new SoulBindingRecipe(outputStack(output), ingredient, positive(energy, "Energy"), xpLevels,
                selector.entity(), selector.category(), selector.rule(), copyInputNbt, id);
    }
}
