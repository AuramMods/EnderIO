package com.enderio.enderio.foundation.block.entity;

import com.enderio.enderio.api.capacitor.ICapacitorExtension;
import com.enderio.core.annotations.UseOnly;
import com.enderio.enderio.api.capacitor.CapacitorData;
import com.enderio.enderio.api.capacitor.CapacitorScalable;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.content.capacitors.CapacitorItem;
import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.block.entity.flags.CapacitorSupport;
import com.enderio.enderio.foundation.energy.PoweredMachineEnergyStorage;
import com.enderio.enderio.foundation.inventory.MachineInventory;
import com.enderio.enderio.foundation.io.TransferUtil;
import com.enderio.enderio.foundation.state.MachineState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.LogicalSide;
import java.util.function.BiFunction;
import net.minecraftforge.energy.IEnergyStorage;

public abstract class PoweredMachineBlockEntity extends MachineBlockEntity implements MachineInstallable {

    public static final BiFunction<PoweredMachineBlockEntity, Direction, IEnergyStorage> ENERGY_STORAGE_PROVIDER = (
            be, side) -> side == null ? be.energyStorage : be.energyStorage.getSided(side);

    private final CapacitorSupport capacitorSupport;
    private CapacitorData capacitorData = CapacitorData.NONE;

    private final EnergyIOMode energyIOMode;
    private final CapacitorScalable scalableEnergyCapacity;
    private final CapacitorScalable scalableMaxEnergyUse;

    private final PoweredMachineEnergyStorage energyStorage;

    public PoweredMachineBlockEntity(BlockEntityType<?> type, BlockPos worldPosition, BlockState blockState,
                                     boolean isIoConfigMutable, CapacitorSupport capacitorSupport, EnergyIOMode energyIOMode,
                                     CapacitorScalable scalableEnergyCapacity, CapacitorScalable scalableMaxEnergyUse) {
        super(type, worldPosition, blockState, isIoConfigMutable);

        this.capacitorSupport = capacitorSupport;
        this.energyIOMode = energyIOMode;
        this.scalableEnergyCapacity = scalableEnergyCapacity;
        this.scalableMaxEnergyUse = scalableMaxEnergyUse;

        // Sanity check for capacitors.
        if (supportsCapacitor() && (!hasInventory() || !getInventory().layout().supportsCapacitor())) {
            throw new IllegalStateException(
                    "A machine which accepts a capacitor must have an inventory with a capacitor slot!");
        }

        energyStorage = new PoweredMachineEnergyStorage(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updatePowerState();
        updateCapacitorState();
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        updatePowerState();
        updateCapacitorData();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        updatePowerState();
    }

    @Override
    protected void onInventoryContentsChanged(int slot) {
        super.onInventoryContentsChanged(slot);
        updateCapacitorData();
    }

    // region Energy Storage

    public PoweredMachineEnergyStorage getEnergyStorage() {
        return energyStorage;
    }

    @UseOnly(LogicalSide.CLIENT)
    public final void clientSetEnergyStored(int energyStored) {
        energyStorage.setEnergyStored(energyStored);
    }

    public final boolean hasEnergy() {
        // If the machine has no capacitor, you cannot interact with it's energy storage
        if (requiresCapacitor() && !isCapacitorInstalled()) {
            return false;
        }

        return energyStorage.getEnergyStored() > 0;
    }

    public final int getMaxEnergyStored() {
        // TODO: Scalable might need redesigned to just scale directly...
        return Math.max(0, scalableEnergyCapacity.scaleI(this::getCapacitorData).get());
    }

    public final int getMaxEnergyUse() {
        return Math.max(0, scalableMaxEnergyUse.scaleI(this::getCapacitorData).get());
    }

    public final EnergyIOMode energyIOMode() {
        return energyIOMode;
    }

    protected void updatePowerState() {
        updateMachineState(MachineState.NO_POWER, energyStorage.getEnergyStored() <= 0);
    }

    // region Distribution

    @Override
    protected int distributeResourcesInterval() {
        return energyIOMode.canOutput() ? 1 : super.distributeResourcesInterval();
    }

    @Override
    protected void distributeResources() {
        super.distributeResources();

        if (energyIOMode.canOutput()) {
            TransferUtil.distributeEnergyEvenly(level, worldPosition, dir -> energyIOMode.canPush(getIOMode(dir)));
        }
    }

    // endregion

    // endregion

    // region Capacitor

    public final CapacitorSupport capacitorSupport() {
        return capacitorSupport;
    }

    public final boolean supportsCapacitor() {
        return capacitorSupport != CapacitorSupport.NONE;
    }

    public final boolean requiresCapacitor() {
        return capacitorSupport == CapacitorSupport.REQUIRED;
    }

    public boolean isCapacitorInstalled() {
        if (!supportsCapacitor()) {
            // TODO: Should this be an exception because we do not support capacitors?
            return false;
        }

        if (level != null && level.isClientSide()) {
            return !getCapacitorItem().isEmpty();
        }

        return !capacitorData.equals(CapacitorData.NONE);
    }

    @UseOnly(LogicalSide.SERVER)
    public CapacitorData getCapacitorData() {
        if (!supportsCapacitor()) {
            throw new IllegalStateException("Unable to get capacitor data, this machine does not support capacitors!");
        }

        return capacitorData;
    }

    public final ItemStack getCapacitorItem() {
        if (!supportsCapacitor() || !hasInventory()) {
            return ItemStack.EMPTY;
        }
        MachineInventory inventory = getInventory();
        return inventory.getStackInSlot(inventory.layout().getCapacitorSlot());
    }

    public final int getCapacitorSlotIndex() {
        if (!hasInventory()) {
            throw new IllegalStateException("Attempt to get capacitor slot for machine with no inventory!");
        }

        var layout = getInventory().layout();
        if (!layout.supportsCapacitor()) {
            throw new IllegalStateException("Unable to get capacitor slot index, inventory has no capacitor slot.");
        }

        return layout.getCapacitorSlot();
    }

    protected void updateCapacitorData() {
        // Wait for the level to be loaded.
        if (level == null) {
            return;
        }

        if (supportsCapacitor()) {
            var capacitorItem = getCapacitorItem();
            var capacitorExtension = capacitorItem.getCapability(ICapacitorExtension.CAPABILITY).orElse(null);
            if (capacitorExtension != null) {
                capacitorData = java.util.Objects.requireNonNullElse(
                    capacitorExtension.getCapacitorData(capacitorItem, level.registryAccess()), CapacitorData.NONE);
            } else {
                capacitorData = CapacitorData.ITEM_DATA.getOrDefault(getCapacitorItem(), CapacitorData.NONE);
            }

            updateCapacitorState();
        }
    }

    private void updateCapacitorState() {
        updateMachineState(MachineState.NO_CAPACITOR, supportsCapacitor() && !isCapacitorInstalled());
    }

    // TODO: Ensure this is called by all machines.
    protected void onMachineUsed() {
        if (supportsCapacitor()) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return;
            }

            var capacitorItem = getCapacitorItem();
            var capacitorExtension = capacitorItem.getCapability(ICapacitorExtension.CAPABILITY).orElse(null);
            if (capacitorExtension != null) {
                capacitorExtension.onMachineUsed(capacitorItem, serverLevel);
                updateCapacitorData();
                setChanged();
            }
        }
    }

    // region MachineInstallable Implementation

    @Override
    public InteractionResult tryItemInstall(ItemStack stack, UseOnContext context) {
        if (!stack.isEmpty() && stack.getItem() instanceof CapacitorItem && supportsCapacitor()) {
            MachineInventory inventory = getInventory();
            int slot = inventory.layout().getCapacitorSlot();
            if (inventory.getStackInSlot(slot).isEmpty() && inventory.isItemValid(slot, stack)) {
                if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
                if (!inventory.insertItem(slot, stack.copyWithCount(1), false).isEmpty()) return InteractionResult.PASS;
                if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) stack.shrink(1);
                return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
            }
        }

        return InteractionResult.PASS;
    }

    // endregion

    // endregion

    // region Serialization

    @Override
    protected void saveAdditional(CompoundTag tag) {
        var registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditional(tag);
        tag.put(MachineNBTKeys.ENERGY_STORED, energyStorage.serializeNBT(registries));
    }

    @Override
    public void load(CompoundTag tag) {
        var registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.load(tag);

        if (tag.contains(MachineNBTKeys.ENERGY_STORED, Tag.TAG_INT)) {
            energyStorage.deserializeNBT(registries, (IntTag) tag.get(MachineNBTKeys.ENERGY_STORED));
        } else if (tag.contains(MachineNBTKeys.ENERGY, Tag.TAG_COMPOUND)) {
            // SUPPORT LEGACY STORAGE FORMAT
            CompoundTag energyTag = tag.getCompound(MachineNBTKeys.ENERGY);

            if (energyTag.contains(MachineNBTKeys.ENERGY_STORED)) {
                energyStorage.setEnergyStored(energyTag.getInt(MachineNBTKeys.ENERGY_STORED));
            }
        }

        updateCapacitorData();
        updatePowerState();
    }

    @Override
    protected void readItemData(ItemStack componentInput) {
        super.readItemData(componentInput);
        energyStorage.setEnergyStored(PoweredMachineEnergyStorage.ITEM_DATA.getOrDefault(componentInput, 0));
        updateCapacitorData();
    }

    @Override
    protected void writeItemData(ItemStack components) {
        super.writeItemData(components);
        PoweredMachineEnergyStorage.ITEM_DATA.set(components, energyStorage.getEnergyStored());
    }

    @Override
    public void removeItemDataFromTag(CompoundTag tag) {
        super.removeItemDataFromTag(tag);
        tag.remove(MachineNBTKeys.ENERGY_STORED);
        tag.remove(MachineNBTKeys.ENERGY);
    }

    // endregion
}
