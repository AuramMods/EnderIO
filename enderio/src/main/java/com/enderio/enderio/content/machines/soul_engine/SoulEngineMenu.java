package com.enderio.enderio.content.machines.soul_engine;

import com.enderio.core.common.network.menu.IntSyncSlot;
import com.enderio.enderio.foundation.fluid.FluidStorageInfo;
import com.enderio.enderio.foundation.fluid.FluidStorageSyncSlot;
import com.enderio.enderio.foundation.menu.PoweredMachineMenu;
import com.enderio.enderio.init.EIOBlockEntities;
import com.enderio.enderio.init.EIOMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

public class SoulEngineMenu extends PoweredMachineMenu<SoulEngineBlockEntity> {

    private final FluidStorageSyncSlot fluidTankSlot;
    private final IntSyncSlot burnRate;
    private final IntSyncSlot energyPerMillibucket;

    public SoulEngineMenu(int containerId, Inventory inventory, SoulEngineBlockEntity blockEntity) {
        super(EIOMenus.SOUL_ENGINE.get(), containerId, inventory, blockEntity);
        addSlots();

        fluidTankSlot = addSyncSlot(
                FluidStorageSyncSlot.readOnly(() -> FluidStorageInfo.of(blockEntity.getFluidTank())));
        burnRate = addSyncSlot(IntSyncSlot.readOnly(blockEntity::getBurnRate));
        energyPerMillibucket = addSyncSlot(IntSyncSlot.readOnly(blockEntity::getEnergyPerMillibucket));
    }

    public SoulEngineMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        super(EIOMenus.SOUL_ENGINE.get(), containerId, playerInventory, buf,
            EIOBlockEntities.SOUL_ENGINE.get());
        addSlots();

        fluidTankSlot = addSyncSlot(FluidStorageSyncSlot.standalone());
        burnRate = addSyncSlot(IntSyncSlot.standalone());
        energyPerMillibucket = addSyncSlot(IntSyncSlot.standalone());
    }

    private void addSlots() {
        addCapacitorSlot(12, 60);
        addPlayerInventorySlots(8, 84);
    }

    public FluidStorageInfo getFluidTank() {
        return fluidTankSlot.get();
    }

    public int getBurnRate() {
        return burnRate.get();
    }

    public int getEnergyPerMillibucket() {
        return energyPerMillibucket.get();
    }
}
