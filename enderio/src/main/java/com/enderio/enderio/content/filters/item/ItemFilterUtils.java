package com.enderio.enderio.content.filters.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class ItemFilterUtils {
    /** Damage has its own filter mode; all other stored item tags must agree in both directions. */
    public static boolean doComponentsMatch(ItemStack referenceStack, ItemStack stack) {
        return comparableTag(referenceStack).equals(comparableTag(stack));
    }

    private static CompoundTag comparableTag(ItemStack stack) {
        CompoundTag tag = stack.hasTag() ? stack.getTag().copy() : new CompoundTag();
        tag.remove("Damage");
        return tag;
    }
}
