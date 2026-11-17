package com.enderio.enderio.foundation.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The built-in glint and lore of Ender IO's special crafting materials. */
public class GlintedItem extends Item {
    private final List<Component> lore;

    public GlintedItem(Properties properties) {
        this(properties, List.of());
    }

    public GlintedItem(Properties properties, List<Component> lore) {
        super(properties);
        this.lore = lore.stream().map(Component::copy).map(component -> (Component) component).toList();
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flags) {
        super.appendHoverText(stack, level, tooltip, flags);
        for (Component line : lore) tooltip.add(line.copy().withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
