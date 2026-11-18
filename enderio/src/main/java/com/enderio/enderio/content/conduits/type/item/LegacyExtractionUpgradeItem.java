package com.enderio.enderio.content.conduits.type.item;

import com.enderio.core.common.util.TooltipUtil;
import com.enderio.enderio.content.conduits.ConduitLang;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class LegacyExtractionUpgradeItem extends Item {
    static final int MAX_ITEMS_PER_SECOND = 64;
    private final int itemsPerSecond;

    public LegacyExtractionUpgradeItem(Properties properties, int itemsPerSecond) {
        super(properties);
        this.itemsPerSecond = itemsPerSecond;
    }

    public int itemsPerSecond() {
        return itemsPerSecond;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(TooltipUtil.styledWithArgs(ConduitLang.ITEM_EFFECTIVE_RATE_TOOLTIP, itemsPerSecond));
    }
}
