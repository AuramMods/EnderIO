package com.enderio.enderio.content.filters.item.limited;

import com.enderio.core.annotations.UseOnly;
import com.enderio.core.common.network.menu.BoolSyncSlot;
import com.enderio.core.common.network.menu.EnumSyncSlot;
import com.enderio.core.common.network.menu.SyncSlot;
import com.enderio.core.common.network.menu.payload.CompoundTagSlotPayload;
import com.enderio.core.common.network.menu.payload.SlotPayload;
import com.enderio.core.common.serialization.StackDataCodecs;
import com.enderio.enderio.content.filters.AbstractFilterMenu;
import com.enderio.enderio.content.filters.item.general.DamageFilterMode;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.core.NonNullList;
import com.enderio.core.common.item.ItemDataKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.LogicalSide;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class LimitedItemFilterMenu extends AbstractFilterMenu<LimitedItemFilter> {

    public static final int SHOULD_COMPARE_COMPONENTS_BUTTON_ID = 2;

    @Nullable
    private final NonNullList<ItemStack> clientItems;

    private final BoolSyncSlot shouldCompareComponentsSyncSlot;
    private final EnumSyncSlot<DamageFilterMode> damageFilterSyncSlot;

    public LimitedItemFilterMenu(@Nullable MenuType<?> menuType, int containerId, Inventory playerInventory,
            FilterAccess filterAccess) {
        super(menuType, containerId, playerInventory, filterAccess);
        this.clientItems = null;

        this.shouldCompareComponentsSyncSlot = addSyncSlot(
                BoolSyncSlot.readOnly(() -> getFilter().shouldCompareComponents()));

        this.damageFilterSyncSlot = addUpdatableSyncSlot(EnumSyncSlot.simple(DamageFilterMode.class,
                () -> EIODataComponents.LIMITED_ITEM_FILTER.getOrDefault(getFilterStack(), LimitedItemFilter.EMPTY)
                        .damageFilterMode(),
                (mode) -> modifyFilterStack(stack -> {
                    var filter = EIODataComponents.LIMITED_ITEM_FILTER.getOrDefault(stack, LimitedItemFilter.EMPTY);
                    EIODataComponents.LIMITED_ITEM_FILTER.set(stack,
                            new LimitedItemFilter(filter.matches(), filter.shouldCompareComponents(), mode));
                    return stack;
                })));

        for (int i = 0; i < LimitedItemFilter.SLOT_COUNT; i++) {
            final int slotIndex = i;
            Supplier<ItemStack> sample = () -> getItemInFilter(slotIndex);
            addSyncSlot(new StockSyncSlot(sample, null));
            addSlot(new LimitedItemFilterSlot(sample,
                    stack -> setItemInFilter(slotIndex, stack), i, 14 + (i % 9) * 18, 27 + 18 * (i / 9)));
        }

        addPlayerInventorySlots(14, 45 + 2 * 18);
    }

    public LimitedItemFilterMenu(@Nullable MenuType<?> menuType, int containerId, Inventory playerInventory) {
        super(menuType, containerId, playerInventory);
        this.clientItems = NonNullList.withSize(LimitedItemFilter.SLOT_COUNT, ItemStack.EMPTY);

        this.shouldCompareComponentsSyncSlot = addSyncSlot(BoolSyncSlot.standalone());

        this.damageFilterSyncSlot = addUpdatableSyncSlot(EnumSyncSlot.standalone(DamageFilterMode.class));

        for (int i = 0; i < LimitedItemFilter.SLOT_COUNT; i++) {
            final int slotIndex = i;
            Supplier<ItemStack> sample = () -> clientItems.get(slotIndex);
            Consumer<ItemStack> update = stack -> clientItems.set(slotIndex, stack);
            addSyncSlot(new StockSyncSlot(sample, update));
            addSlot(new LimitedItemFilterSlot(sample, update, i, 14 + (i % 9) * 18, 27 + 18 * (i / 9)));
        }

        addPlayerInventorySlots(14, 45 + 2 * 18);
    }

    @Override
    protected ItemDataKey<LimitedItemFilter> dataComponentType() {
        return EIODataComponents.LIMITED_ITEM_FILTER;
    }

    @Override
    protected LimitedItemFilter defaultFilter() {
        return LimitedItemFilter.EMPTY;
    }

    public boolean shouldCompareComponents() {
        return shouldCompareComponentsSyncSlot.get();
    }

    public DamageFilterMode damageFilterMode() {
        return damageFilterSyncSlot.get();
    }

    @UseOnly(LogicalSide.CLIENT)
    public void setDamageFilterMode(DamageFilterMode mode) {
        damageFilterSyncSlot.set(mode);
        updateSlot(damageFilterSyncSlot);
    }

    @UseOnly(LogicalSide.SERVER)
    private ItemStack getItemInFilter(int slotIndex) {
        var filter = getFilter();
        if (slotIndex >= filter.matches().size()) {
            return ItemStack.EMPTY;
        }

        return filter.matches().get(slotIndex);
    }

    @UseOnly(LogicalSide.SERVER)
    private void setItemInFilter(int slotIndex, ItemStack stack) {
        modifyFilter(filter -> {
            var matches = NonNullList.withSize(LimitedItemFilter.SLOT_COUNT, ItemStack.EMPTY);
            for (int i = 0; i < matches.size(); i++) {
                matches.set(i, i < filter.matches().size() ? filter.matches().get(i) : ItemStack.EMPTY);
            }

            matches.set(slotIndex, stack);

            return new LimitedItemFilter(matches, filter.shouldCompareComponents(), filter.damageFilterMode());
        });
    }

    @Override
    public void doClick(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < slots.size() && getSlot(slotId) instanceof LimitedItemFilterSlot limitedSlot) {
            if (clickType != ClickType.PICKUP && clickType != ClickType.QUICK_MOVE) {
                return;
            }

            ItemStack cursor = getCarried();
            if (!cursor.isEmpty() && !limitedSlot.isEmpty()) {
                ItemStack current = limitedSlot.getResource();
                // Keep the usual stack cap for cursor edits without reducing a larger saved target.
                if (ItemStack.isSameItem(cursor, current) && current.getCount() < current.getMaxStackSize()) {
                    limitedSlot.setResource(current.copyWithCount(current.getCount() + cursor.getCount()));
                }
                return;
            }
        }

        super.doClick(slotId, button, clickType, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == SHOULD_COMPARE_COMPONENTS_BUTTON_ID) {
            modifyFilter(filter -> new LimitedItemFilter(filter.matches(), !filter.shouldCompareComponents(),
                    filter.damageFilterMode()));
            return true;
        }

        return super.clickMenuButton(player, id);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private static final class StockSyncSlot implements SyncSlot {
        private final Supplier<ItemStack> sample;
        private final @Nullable Consumer<ItemStack> update;
        private ItemStack previous = ItemStack.EMPTY;

        private StockSyncSlot(Supplier<ItemStack> sample, @Nullable Consumer<ItemStack> update) {
            this.sample = sample;
            this.update = update;
        }

        @Override
        public ChangeType detectChanges() {
            var current = sample.get();
            if (ItemStack.matches(current, previous)) return ChangeType.NONE;
            previous = current.copy();
            return ChangeType.FULL;
        }

        @Override
        public SlotPayload createPayload(Level level, ChangeType changeType) {
            return new CompoundTagSlotPayload(StackDataCodecs.saveItem(sample.get()));
        }

        @Override
        public void unpackPayload(Level level, SlotPayload payload) {
            if (update != null && payload instanceof CompoundTagSlotPayload tag) {
                update.accept(StackDataCodecs.loadItem(tag.value()));
            }
        }
    }
}
