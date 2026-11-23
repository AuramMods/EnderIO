package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.enderio.enderio.content.fire_crafting.FireCraftingRecipe;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister
@ZenCodeType.Name("mods.enderio.FireOutput")
public record FireOutput(FireCraftingRecipe.Result value) {
    @ZenCodeType.Method
    public static FireOutput item(IItemStack stack, int minCount, int maxCount, float chance) {
        if (minCount < 1 || maxCount < minCount || maxCount == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Fire crafting output needs 1 <= minCount <= maxCount < 2147483647");
        }
        if (!Float.isFinite(chance) || chance < 0 || chance > 1) {
            throw new IllegalArgumentException("Fire crafting chance must be between 0 and 1");
        }
        return new FireOutput(new FireCraftingRecipe.Result(
                ProcessingRecipeManager.outputStack(stack), minCount, maxCount, chance));
    }
}
