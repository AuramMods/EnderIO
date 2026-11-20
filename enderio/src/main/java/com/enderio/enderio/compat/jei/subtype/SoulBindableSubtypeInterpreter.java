package com.enderio.enderio.compat.jei.subtype;

import com.enderio.enderio.api.soul.SoulBoundUtils;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class SoulBindableSubtypeInterpreter implements ISubtypeInterpreter<ItemStack> {
    @Override
    public @Nullable Object getSubtypeData(ItemStack ingredient, UidContext context) {
        var soul = SoulBoundUtils.getBoundSoul(ingredient);
        if (soul.isEmpty()) {
            return null;
        }

        return context == UidContext.Recipe ? soul.entityTypeId() : soul;
    }

    @Override
    public String getLegacyStringSubtypeInfo(ItemStack ingredient, UidContext context) {
        var soul = SoulBoundUtils.getBoundSoul(ingredient);
        return soul.isEmpty() ? "" : soul.entityTypeId().toString();
    }
}
