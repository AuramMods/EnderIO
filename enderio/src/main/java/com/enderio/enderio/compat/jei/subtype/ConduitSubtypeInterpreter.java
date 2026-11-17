package com.enderio.enderio.compat.jei.subtype;

import com.enderio.enderio.api.EnderIODataComponents;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class ConduitSubtypeInterpreter implements ISubtypeInterpreter<ItemStack> {

    @Override
    public @Nullable Object getSubtypeData(ItemStack ingredient, UidContext context) {
        var conduit = EnderIODataComponents.CONDUIT.getId(ingredient);
        if (conduit != null) {
            return conduit.toString();
        }

        return null;
    }

    @Override
    public String getLegacyStringSubtypeInfo(ItemStack ingredient, UidContext context) {
        var conduit = EnderIODataComponents.CONDUIT.getId(ingredient);
        if (conduit != null) {
            return conduit.toString();
        }

        return "";
    }
}
