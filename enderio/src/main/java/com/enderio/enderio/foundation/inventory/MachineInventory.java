package com.enderio.enderio.foundation.inventory;

import com.enderio.enderio.api.io.IOConfigurable;
import com.enderio.enderio.foundation.state.MachineState;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import com.enderio.core.common.serialization.StackDataCodecs;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.Tag;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.IntConsumer;

/**
 * A machine inventory.
 * Configured and controlled by a machine's {@link IOConfigurable} and a {@link MachineInventoryLayout}.
 */
public class MachineInventory extends ItemStackHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final IOConfigurable ioConfigurable;
    private final MachineInventoryLayout layout;
    private ListTag overflowItems = new ListTag();
    private IntConsumer changeListener = i -> {
    };

    /**
     * Create a new machine inventory.
     */
    public MachineInventory(IOConfigurable ioConfigurable, MachineInventoryLayout layout) {
        super(layout.getSlotCount());
        this.ioConfigurable = ioConfigurable;
        this.layout = layout;
    }

    // TODO: Why are we not calling changeListener in onContentsChanged?
    public void addSlotChangedCallback(IntConsumer callback) {
        changeListener = changeListener.andThen(callback);
    }

    /**
     * Get the inventory layout.
     */
    public final MachineInventoryLayout getLayout() {
        return layout;
    }

    public final MachineInventoryLayout layout() {
        return layout;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return layout.isItemValid(slot, stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        return layout.getStackLimit(slot);
    }

    @Nullable
    public IItemHandler getForSide(@Nullable Direction side) {
        if (side == null) {
            return new Wrapped(this, null);
        }

        if (ioConfigurable.getIOMode(side).canConnect()) {
            return new Wrapped(this, side);
        }

        return null;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        ItemStack before = getStackInSlot(slot).copy();
        ItemStack remainder = super.insertItem(slot, stack, simulate);
        if (!simulate && !ItemStack.matches(before, getStackInSlot(slot))) {
            changeListener.accept(slot);
        }
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack before = getStackInSlot(slot).copy();
        ItemStack extracted = super.extractItem(slot, amount, simulate);
        if (!simulate && !ItemStack.matches(before, getStackInSlot(slot))) {
            changeListener.accept(slot);
        }
        return extracted;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        ItemStack before = getStackInSlot(slot).copy();
        super.setStackInSlot(slot, stack);
        if (!ItemStack.matches(before, stack)) {
            changeListener.accept(slot);
        }
    }

    /** Native slot NBT used when carrying a machine inventory in an item. */
    public void copyFromItem(CompoundTag contents) {
        deserializeNBT(contents);
    }

    public CompoundTag toItemContents() {
        return serializeNBT();
    }

    // TODO: not a fan of this pattern.
    public void updateMachineState(MachineState state, boolean add) {

    }

    /**
     * Creates a snapshot of the inventory with only the MultiSlotAccess slots set. Only used when doing chained simulations.
     * @return An inventory with copied itemstacks in the MultiSlotAccess slots.
     */
    public MachineInventory snapshot(MultiSlotAccess slots) {
        MachineInventory machineInventory = new MachineInventory(ioConfigurable, layout);
        for (SingleSlotAccess outputAccess : slots.getAccesses()) {
            outputAccess.setStackInSlot(machineInventory, outputAccess.getItemStack(this).copy());
        }
        return machineInventory;
    }

    @Override
    public CompoundTag serializeNBT() {
        var items = new ListTag();
        for (int slot = 0; slot < getSlots(); slot++) {
            if (!stacks.get(slot).isEmpty()) {
                var item = StackDataCodecs.saveItem(stacks.get(slot));
                item.putInt("Slot", slot);
                items.add(item);
            }
        }
        overflowItems.forEach(item -> items.add(item.copy()));
        var tag = new CompoundTag();
        tag.put("Items", items);
        tag.putInt("Size", layout.getSlotCount());
        return tag;
    }

    /** Keep the current layout, including when loading an older inventory size. */
    @Override
    public void deserializeNBT(CompoundTag tag) {
        var loaded = NonNullList.withSize(layout.getSlotCount(), ItemStack.EMPTY);
        var overflow = new ListTag();
        var seen = new java.util.HashSet<Integer>();
        var items = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            var item = items.getCompound(i);
            int slot = item.getInt("Slot");
            if (!seen.add(slot)) {
                throw new IllegalArgumentException("Duplicate machine inventory slot: " + slot);
            }
            if (slot < 0 || slot >= loaded.size()) {
                // Preserve unavailable slots if a different layout is restored later.
                overflow.add(item.copy());
                LOGGER.warn("Preserving item from slot {} outside the current inventory layout", slot);
            } else {
                var stackData = item.copy();
                stackData.remove("Slot");
                loaded.set(slot, StackDataCodecs.loadItem(stackData));
            }
        }
        var previous = stacks;
        stacks = loaded;
        overflowItems = overflow;
        onLoad();
        for (int slot = 0; slot < stacks.size(); slot++) {
            if (slot >= previous.size() || !ItemStack.matches(previous.get(slot), stacks.get(slot))) {
                onContentsChanged(slot);
                changeListener.accept(slot);
            }
        }
    }

    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        return serializeNBT();
    }

    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        deserializeNBT(tag);
    }

    private record Wrapped(MachineInventory master, @Nullable Direction side) implements IItemHandler {

        @Override
        public int getSlots() {
            return master.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return master.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            // Check we allow insertion on the slot
            if (!master.getLayout().canInsert(slot)) {
                return stack;
            }

            // Check we allow input to the block on this side
            if (side != null && !master.ioConfigurable.getIOMode(side).canInput()) {
                return stack;
            }

            return master.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Check we allow extraction on the slot
            if (!master.getLayout().canExtract(slot)) {
                return ItemStack.EMPTY;
            }

            // Check we allow output from the block on this side
            if (side != null && !master.ioConfigurable.getIOMode(side).canOutput()) {
                return ItemStack.EMPTY;
            }

            return master.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return master.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return master.isItemValid(slot, stack);
        }
    }
}
