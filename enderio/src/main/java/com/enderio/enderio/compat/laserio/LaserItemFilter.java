package com.enderio.enderio.compat.laserio;

import com.direwolf20.laserio.common.items.filters.BaseFilter;
import com.direwolf20.laserio.common.items.filters.FilterBasic;
import com.enderio.enderio.api.filter.ItemFilter;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public class LaserItemFilter implements ItemFilter {

    private final java.util.function.Supplier<ItemStack> container;

    public LaserItemFilter(ItemStack container) {
        this(() -> container);
    }

    public LaserItemFilter(java.util.function.Supplier<ItemStack> container) {
        this.container = container;
    }

    @Override
    public ItemStack test(@Nullable IItemHandler target, ItemStack stack) {
        ItemStack container = this.container.get();
        if (container.isEmpty()) {
            return stack;
        }
        final boolean isAllowList = BaseFilter.getAllowList(container);
        final boolean shouldCompareNbt = BaseFilter.getCompareNBT(container);

        var handler = FilterBasic.getInventory(container);
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack match = handler.getStackInSlot(i);
            if (!match.isEmpty()) {
                if (shouldCompareNbt ? ItemStack.isSameItemSameTags(match, stack)
                        : ItemStack.isSameItem(match, stack)) {
                    return isAllowList ? stack : ItemStack.EMPTY;
                }
            }
        }

        return isAllowList ? ItemStack.EMPTY : stack;
    }
}
