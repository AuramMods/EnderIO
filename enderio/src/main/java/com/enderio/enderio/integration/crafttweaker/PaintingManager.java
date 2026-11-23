package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.enderio.enderio.content.machines.painting.PaintingRecipe;
import com.enderio.enderio.init.EIORecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister
@ZenCodeType.Name("mods.enderio.PaintingManager")
public final class PaintingManager extends ProcessingRecipeManager<PaintingRecipe> {
    @Override
    public RecipeType<PaintingRecipe> getRecipeType() {
        return EIORecipes.PAINTING.type().get();
    }

    @ZenCodeType.Method
    public void addRecipe(String name, IIngredient template, IItemStack output) {
        add(new PaintingRecipe(template.asVanillaIngredient(), outputStack(output), scriptId(name)));
    }

    @ZenCodeType.Method
    public void replaceRecipe(String id, IIngredient template, IItemStack output) {
        replace(new PaintingRecipe(template.asVanillaIngredient(), outputStack(output), new ResourceLocation(id)));
    }
}
