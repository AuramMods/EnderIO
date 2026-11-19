package com.enderio.enderio.content.filters.redstone;

import com.enderio.core.common.menu.BaseEnderMenu;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

abstract class HandheldRedstoneFilterMenu extends BaseEnderMenu {
    private final InteractionHand hand;
    private final ItemStack openedStack;
    private final int lockedSlot;

    protected HandheldRedstoneFilterMenu(MenuType<?> type, int containerId, Inventory inventory, InteractionHand hand) {
        super(type, containerId, inventory);
        this.hand = hand;
        this.openedStack = inventory.player.getItemInHand(hand);
        this.lockedSlot = hand == InteractionHand.MAIN_HAND ? inventory.selected : -1;
        addPlayerInventorySlots(14, 119);
    }

    protected ItemStack filterStack() {
        // Vanilla menu synchronization can replace the client's inventory stack.
        var held = getPlayerInventory().player.getItemInHand(hand);
        return held.is(openedStack.getItem()) ? held : openedStack;
    }

    public boolean acceptsEdit(int containerId, Player player) {
        return this.containerId == containerId && !player.isSpectator() && stillValid(player);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().isClientSide || player.getItemInHand(hand) == openedStack;
    }

    @Override
    protected Slot createPlayerInventorySlot(Inventory inventory, int index, int x, int y) {
        if (index != lockedSlot) return super.createPlayerInventorySlot(inventory, index, x, y);
        return new Slot(inventory, index, x, y) {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        };
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
