package com.enderio.enderio.compat.jei.subtype;

import com.enderio.enderio.api.soul.SoulBoundUtils;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.world.item.ItemStack;

public class SoulBindableSubtypeInterpreter implements IIngredientSubtypeInterpreter<ItemStack> {
    @Override
    public String apply(ItemStack ingredient, UidContext context) {
        var soul = SoulBoundUtils.getBoundSoul(ingredient);
        if (soul.isEmpty()) {
            return NONE;
        }

        if (context == UidContext.Recipe || soul.entityTag().isEmpty()) {
            return soul.entityTypeId().toString();
        }
        return soul.entityTypeId().toString() + soul.entityTag();
    }
}
