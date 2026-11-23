package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.action.recipe.ActionRemoveRecipe;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.ingredient.IIngredientWithAmount;
import com.enderio.core.common.recipes.OutputStack;
import com.enderio.enderio.content.enchanter.EnchanterRecipe;
import com.enderio.enderio.init.EIORecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import org.openzen.zencode.java.ZenCodeType;

import java.util.List;

@ZenRegister
@ZenCodeType.Name("mods.enderio.EnchantingManager")
public final class EnchantingManager extends ProcessingRecipeManager<EnchanterRecipe> {
    @Override
    public RecipeType<EnchanterRecipe> getRecipeType() {
        return EIORecipes.ENCHANTING.type().get();
    }

    @ZenCodeType.Method
    public void addRecipe(String name, IIngredientWithAmount catalyst, Enchantment enchantment, int costMultiplier) {
        add(create(scriptId(name), catalyst, enchantment, costMultiplier));
    }

    @ZenCodeType.Method
    public void replaceRecipe(String id, IIngredientWithAmount catalyst, Enchantment enchantment, int costMultiplier) {
        replace(create(new ResourceLocation(id), catalyst, enchantment, costMultiplier));
    }

    @ZenCodeType.Method
    public List<EnchanterRecipe> getRecipesByEnchantment(Enchantment enchantment) {
        return getAllRecipes().stream().filter(recipe -> recipe.enchantment().value() == enchantment).toList();
    }

    @ZenCodeType.Method
    public void removeByEnchantment(Enchantment enchantment) {
        CraftTweakerAPI.apply(new ActionRemoveRecipe<>(this, recipe -> recipe.enchantment().value() == enchantment,
                action -> "Removing Enchanter recipes for " + BuiltInRegistries.ENCHANTMENT.getKey(enchantment)));
    }

    @Override
    protected List<OutputStack> outputs(EnchanterRecipe recipe) {
        return List.of(OutputStack.of(recipe.getBookForLevel(1)));
    }

    private static EnchanterRecipe create(ResourceLocation id, IIngredientWithAmount catalyst, Enchantment enchantment,
            int costMultiplier) {
        return new EnchanterRecipe(BuiltInRegistries.ENCHANTMENT.wrapAsHolder(enchantment),
                positive(costMultiplier, "Cost multiplier"), sized(catalyst), id);
    }
}
