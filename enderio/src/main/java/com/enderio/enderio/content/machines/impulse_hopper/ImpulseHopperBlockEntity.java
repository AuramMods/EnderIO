package com.enderio.enderio.content.machines.impulse_hopper;

import com.enderio.enderio.api.capacitor.CapacitorModifier;
import com.enderio.enderio.api.capacitor.QuadraticScalable;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import com.enderio.enderio.foundation.block.entity.flags.CapacitorSupport;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.inventory.MultiSlotAccess;
import com.enderio.enderio.init.EIOBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class ImpulseHopperBlockEntity extends PoweredMachineBlockEntity {
    public static final QuadraticScalable ENERGY_CAPACITY = new QuadraticScalable(CapacitorModifier.ENERGY_CAPACITY,
            MachinesConfig.COMMON.ENERGY.IMPULSE_HOPPER_CAPACITY);
    public static final QuadraticScalable ENERGY_USAGE = new QuadraticScalable(CapacitorModifier.ENERGY_USE,
            MachinesConfig.COMMON.ENERGY.IMPULSE_HOPPER_USAGE);
    private static final int ENERGY_USAGE_PER_ITEM = 10;
    private static final int MIN_BATCH_INTERVAL = 20;

    public static final MultiSlotAccess INPUT = new MultiSlotAccess();
    public static final MultiSlotAccess OUTPUT = new MultiSlotAccess();
    public static final MultiSlotAccess GHOST = new MultiSlotAccess();

    public ImpulseHopperBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(EIOBlockEntities.IMPULSE_HOPPER.get(), worldPosition, blockState, true, CapacitorSupport.REQUIRED,
                EnergyIOMode.Input, ENERGY_CAPACITY, ENERGY_USAGE);
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ImpulseHopperMenu(containerId, inventory, this);
    }

    @Override
    public MachineInventoryLayout createInventoryLayout() {
        return MachineInventoryLayout.builder()
                .inputSlot(6,
                        (integer, itemStack) -> ItemStack.isSameItemSameTags(itemStack,
                                GHOST.get(integer).getItemStack(this)))
                .slotAccess(INPUT)
                .outputSlot(6)
                .slotAccess(OUTPUT)
                .ghostSlot(6)
                .slotAccess(GHOST)
                .capacitor()
                .build();
    }

    @Override
    public void serverTick() {
        if (shouldActTick() && shouldPassItems()) {
            passItems();
        }
        super.serverTick();
    }

    @Override
    public boolean isActive() {
        return canAct() && hasEnergy();
    }

    public boolean shouldActTick() {
        return canAct(ticksForAction());
    }

    public int ticksForAction() {
        int rate = Math.max(1, getMaxEnergyUse());
        // A batch has a fixed cost. Allow enough ticks between batches for the configured average draw.
        int energyTicks = 1 + (getBatchCost() - 1) / rate;
        return Math.max(MIN_BATCH_INTERVAL, energyTicks);
    }

    public boolean canPass(int slot) {
        ItemStack input = INPUT.get(slot).getItemStack(this);
        ItemStack ghost = GHOST.get(slot).getItemStack(this);
        return ghost.isEmpty() || (ItemStack.isSameItemSameTags(input, ghost) && input.getCount() >= ghost.getCount());
    }

    public boolean canHoldAndMerge(int slot) {
        ItemStack ghost = GHOST.get(slot).getItemStack(this);
        if (ghost.isEmpty()) return true;
        var outputSlot = OUTPUT.get(slot);
        ItemStack output = outputSlot.getItemStack(this);
        int limit = Math.min(ghost.getMaxStackSize(), getInventory().getSlotLimit(outputSlot.getIndex()));
        return (output.isEmpty() || ItemStack.isSameItemSameTags(output, ghost))
            && ghost.getCount() <= limit - output.getCount();
    }

    public boolean shouldPassItems() {
        int cost = getBatchCost();
        if (cost <= 0 || !hasEnergy() || getMaxEnergyUse() <= 0 || getEnergyStorage().getEnergyStored() < cost) {
            return false;
        }
        for (int i = 0; i < GHOST.size(); i++) {
            if (!canPass(i) || !canHoldAndMerge(i)) {
                return false;
            }
        }
        return true;
    }

    private int getBatchCost() {
        int items = 0;
        for (var slot : GHOST.getAccesses()) {
            items += slot.getItemStack(this).getCount();
        }
        return items * ENERGY_USAGE_PER_ITEM;
    }

    private void passItems() {
        getEnergyStorage().takeEnergy(getBatchCost());
        for (int i = 0; i < GHOST.size(); i++) {
            int moved = GHOST.get(i).getItemStack(this).getCount();
            if (moved == 0) {
                continue;
            }
            var inputSlot = INPUT.get(i);
            var outputSlot = OUTPUT.get(i);
            ItemStack input = inputSlot.getItemStack(this);
            ItemStack output = outputSlot.getItemStack(this);
            outputSlot.setStackInSlot(this, output.isEmpty() ? input.copyWithCount(moved) : output.copyWithCount(output.getCount() + moved));
            inputSlot.setStackInSlot(this, input.copyWithCount(input.getCount() - moved));
        }
    }

    public boolean ghostSlotHasItem(int slot) {
        return !GHOST.get(slot).getItemStack(this).isEmpty();
    }
}
