package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
import com.enderio.enderio.content.machines.alloy.SmeltingRecipeInheritance;
import com.enderio.enderio.init.EIORecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister
@ZenCodeType.Name("mods.enderio.AlloySmeltingManager")
public final class AlloySmeltingManager extends AlloyRecipeManager {
    @Override
    public RecipeType<AlloySmeltingRecipe> getRecipeType() {
        return EIORecipes.ALLOY_SMELTING.type().get();
    }

    @ZenCodeType.Method
    public void excludeFurnaceRecipe(String furnaceId) {
        var id = new ResourceLocation(furnaceId);
        CraftTweakerAPI.apply(new EnderIOAction("Excluding furnace recipe " + id + " from alloy smelting",
                () -> SmeltingRecipeInheritance.exclude(CraftTweakerAPI.getAccessibleElementsProvider().recipeManager(), id)));
    }
}
