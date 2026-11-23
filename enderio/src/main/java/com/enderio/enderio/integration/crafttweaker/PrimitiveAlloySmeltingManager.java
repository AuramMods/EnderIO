package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
import com.enderio.enderio.content.machines.alloy.PrimitiveAlloySmeltingRecipe;
import com.enderio.enderio.init.EIORecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister
@ZenCodeType.Name("mods.enderio.PrimitiveAlloySmeltingManager")
public final class PrimitiveAlloySmeltingManager extends AlloyRecipeManager {
    @Override
    public RecipeType<AlloySmeltingRecipe> getRecipeType() {
        return EIORecipes.PRIMITIVE_ALLOY_SMELTING.type().get();
    }

    @Override
    protected AlloySmeltingRecipe finish(AlloySmeltingRecipe recipe, ResourceLocation id) {
        return new PrimitiveAlloySmeltingRecipe(recipe, id);
    }
}
