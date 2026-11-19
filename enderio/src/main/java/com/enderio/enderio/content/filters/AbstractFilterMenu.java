package com.enderio.enderio.content.filters;

import com.enderio.core.annotations.UseOnly;
import com.enderio.core.common.menu.BaseEnderMenu;
import com.enderio.core.common.network.menu.IntSyncSlot;
import com.enderio.core.common.item.ItemDataKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public abstract class AbstractFilterMenu<T> extends BaseEnderMenu {

    public static final int BACK_BUTTON_ID = 0;

    @Nullable
    private final FilterAccess filterAccess;

    private final IntSyncSlot playerInventorySlot;

    /**
     * Server menu constructor
     */
    protected AbstractFilterMenu(@Nullable MenuType<?> menuType, int containerId, Inventory playerInventory,
            FilterAccess filterAccess) {
        super(menuType, containerId, playerInventory);
        this.filterAccess = filterAccess;

        this.playerInventorySlot = addSyncSlot(IntSyncSlot.readOnly(this::getPlayerInventorySlot));
    }

    /**
     * Client menu constructor.
     * The opening slot is synchronized by the menu.
     */
    protected AbstractFilterMenu(@Nullable MenuType<?> menuType, int containerId, Inventory playerInventory) {
        super(menuType, containerId, playerInventory);
        this.filterAccess = null;

        this.playerInventorySlot = addSyncSlot(IntSyncSlot.standalone());
    }

    protected abstract ItemDataKey<T> dataComponentType();
    protected abstract T defaultFilter();

    @UseOnly(LogicalSide.SERVER)
    protected T getFilter() {
        return dataComponentType().getOrDefault(getFilterStack(), defaultFilter());
    }

    @UseOnly(LogicalSide.SERVER)
    protected void setFilter(T filter) {
        var stack = getFilterStack().copy();
        dataComponentType().set(stack, filter);
        setFilterStack(stack);
    }

    @UseOnly(LogicalSide.SERVER)
    protected void modifyFilter(Function<T, T> modifier) {
        setFilter(modifier.apply(getFilter()));
    }

    @UseOnly(LogicalSide.SERVER)
    protected ItemStack getFilterStack() {
        return Objects.requireNonNull(filterAccess).getFilterItem();
    }

    @UseOnly(LogicalSide.SERVER)
    protected void setFilterStack(ItemStack stack) {
        Objects.requireNonNull(filterAccess).setFilterItem(stack);
    }

    @UseOnly(LogicalSide.SERVER)
    protected void modifyFilterStack(Function<ItemStack, ItemStack> modifier) {
        setFilterStack(modifier.apply(getFilterStack()));
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().isClientSide() || Objects.requireNonNull(filterAccess).stillValid(player);
    }

    @Override
    protected Slot createPlayerInventorySlot(Inventory inventory, int slot, int x, int y) {
        return new Slot(inventory, slot, x, y) {
            @Override
            public boolean mayPickup(Player player) {
                return slot != playerInventorySlot.get();
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return slot != playerInventorySlot.get();
            }
        };
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BACK_BUTTON_ID) {
            if (filterAccess == null || !filterAccess.goBack()) {
                this.getPlayerInventory().player.closeContainer();
            }
            return true;
        }

        return super.clickMenuButton(player, id);
    }

    @UseOnly(LogicalSide.SERVER)
    private int getPlayerInventorySlot() {
        return filterAccess instanceof HandFilterAccess hand ? hand.inventorySlot : -1;
    }

    @Override
    public void doClick(int slotId, int button, ClickType clickType, Player player) {
        if (clickType == ClickType.SWAP && button == playerInventorySlot.get()) return;
        if (slotId >= 0 && slotId < slots.size() && getSlot(slotId) instanceof FilterSlot<?> filterSlot) {
            // Only allow PICKUP (click) or QUICK_MOVE (shift + click) events.
            if (clickType != ClickType.PICKUP && clickType != ClickType.QUICK_MOVE) {
                return;
            }

            ItemStack carriedItem = this.getCarried();
            if (carriedItem.isEmpty() && !filterSlot.isEmpty()) {
                filterSlot.clearResource();
                return;
            }
        }

        super.doClick(slotId, button, clickType, player);
    }

    public sealed interface FilterAccess {
        ItemStack getFilterItem();

        void setFilterItem(ItemStack stack);

        boolean stillValid(Player player);

        boolean goBack();
    }

    public static final class HandFilterAccess implements FilterAccess {
        private final Player player;
        private final InteractionHand hand;
        private final int inventorySlot;
        private ItemStack stack;

        public HandFilterAccess(Player player, InteractionHand hand, ItemStack stack) {
            this.player = player;
            this.hand = hand;
            this.inventorySlot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
            this.stack = stack;
        }

        @Override
        public ItemStack getFilterItem() {
            return stack.copy();
        }

        @Override
        public void setFilterItem(ItemStack stack) {
            player.setItemInHand(hand, stack);
            this.stack = stack;
        }

        @Override
        public boolean stillValid(Player player) {
            return this.player == player && player.getItemInHand(hand) == stack
                && (hand == InteractionHand.OFF_HAND || player.getInventory().selected == inventorySlot);
        }

        @Override
        public boolean goBack() {
            return false;
        }
    }

    public static final class InventoryFilterAccess implements FilterAccess {
        private ItemStack stack;
        private final IItemHandlerModifiable itemHandler;
        private final int slot;
        private final @Nullable Runnable goBackRunnable;
        private final BooleanSupplier inventoryStillValid;

        public InventoryFilterAccess(ItemStack stack, IItemHandlerModifiable itemHandler, int slot,
                @Nullable Runnable goBackRunnable) {
            this(stack, itemHandler, slot, goBackRunnable, () -> true);
        }

        public InventoryFilterAccess(ItemStack stack, IItemHandlerModifiable itemHandler, int slot,
                @Nullable Runnable goBackRunnable, BooleanSupplier inventoryStillValid) {
            this.stack = stack;
            this.itemHandler = itemHandler;
            this.slot = slot;
            this.goBackRunnable = goBackRunnable;
            this.inventoryStillValid = inventoryStillValid;
        }

        @Override
        public ItemStack getFilterItem() {
            return stack.copy();
        }

        @Override
        public void setFilterItem(ItemStack stack) {
            // Mainly just to deal with handlers that either return a copy or need to
            // setChanged.
            itemHandler.setStackInSlot(slot, stack);
            this.stack = stack;
        }

        @Override
        public boolean stillValid(Player player) {
            return inventoryStillValid.getAsBoolean() && itemHandler.getStackInSlot(slot).equals(stack);
        }

        @Override
        public boolean goBack() {
            if (goBackRunnable != null) {
                goBackRunnable.run();
                return true;
            } else {
                return false;
            }
        }
    }
}
