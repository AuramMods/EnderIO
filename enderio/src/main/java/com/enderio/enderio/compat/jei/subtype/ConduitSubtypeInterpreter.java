package com.enderio.enderio.compat.jei.subtype;

import com.enderio.enderio.api.EnderIODataComponents;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.world.item.ItemStack;

public class ConduitSubtypeInterpreter implements IIngredientSubtypeInterpreter<ItemStack> {

    @Override
    public String apply(ItemStack ingredient, UidContext context) {
        var conduit = EnderIODataComponents.CONDUIT.getId(ingredient);
        return conduit == null ? NONE : conduit.toString();
    }
}
