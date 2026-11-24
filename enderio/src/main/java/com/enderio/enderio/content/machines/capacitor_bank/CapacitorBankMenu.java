package com.enderio.enderio.content.machines.capacitor_bank;

import com.enderio.core.common.menu.BaseBlockEntityMenu;
import com.enderio.core.common.network.menu.EnumSyncSlot;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.foundation.io.energy.ILargeMachineEnergyStorage;
import com.enderio.enderio.init.EIOBlockEntities;
import com.enderio.enderio.init.EIOMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Objects;

public class CapacitorBankMenu extends BaseBlockEntityMenu<CapacitorBankBlockEntity> {

    private final EnumSyncSlot<RedstoneControl> redstoneControlSlot;

    public CapacitorBankMenu(int containerId, Inventory playerInventory, CapacitorBankBlockEntity blockEntity) {
        super(EIOMenus.CAPACITOR_BANK.get(), containerId, playerInventory, blockEntity);

        redstoneControlSlot = addUpdatableSyncSlot(EnumSyncSlot.simple(RedstoneControl.class,
            blockEntity::getRedstoneControl, blockEntity::setNetworkRedstoneControl));

        addPlayerInventorySlots(8, 84);
    }

    public CapacitorBankMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        super(EIOMenus.CAPACITOR_BANK.get(), containerId, playerInventory, buf,
            EIOBlockEntities.CAPACITOR_BANKS.values().stream().map(net.minecraftforge.registries.RegistryObject::get).toArray(BlockEntityType[]::new));

        redstoneControlSlot = addUpdatableSyncSlot(EnumSyncSlot.standalone(RedstoneControl.class));

        addPlayerInventorySlots(8, 84);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(getBlockEntity(), player);
    }

    public boolean canConfigure(Player player, CapacitorBankBlockEntity target) {
        var openedBank = getBlockEntity();
        return stillValid(player) && openedBank.hasActiveNode() && target.hasActiveNode()
            && openedBank.getNetwork() == target.getNetwork();
    }

    //No inv
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public LargeEnergyStorageInfo getEnergyStorage() {
        CapacitorBankManager.CapacitorSyncData data = CapacitorBankManager.getData(getBlockEntity().getUuid());
        if (data == null) {
            return LargeEnergyStorageInfo.EMPTY;
        }
        return new LargeEnergyStorageInfo(data.storedEnergy(), data.capacity());
    }

    public RedstoneControl getRedstoneControl() {
        return Objects.requireNonNull(redstoneControlSlot).get();
    }

    public void setRedstoneControl(RedstoneControl redstoneControl) {
        Objects.requireNonNull(redstoneControlSlot).set(redstoneControl);
        updateSlot(redstoneControlSlot);
    }

    public record LargeEnergyStorageInfo(Long energyStored, Long maxEnergyStored) implements ILargeMachineEnergyStorage {

        public static final LargeEnergyStorageInfo EMPTY = new LargeEnergyStorageInfo(0L,0L);

        @Override
        public int receiveEnergy(int i, boolean b) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int extractEnergy(int i, boolean b) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getEnergyStored() {
            return com.enderio.core.common.util.LongMathUtil.nonNegativeInt(energyStored);
        }

        @Override
        public int getMaxEnergyStored() {
            return com.enderio.core.common.util.LongMathUtil.nonNegativeInt(maxEnergyStored);
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return false;
        }

        @Override
        public long getLargeEnergyStored() {
            return energyStored;
        }

        @Override
        public long getLargeMaxEnergyStored() {
            return maxEnergyStored;
        }
    }
}
