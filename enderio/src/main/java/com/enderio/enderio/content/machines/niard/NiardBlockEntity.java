package com.enderio.enderio.content.machines.niard;

import com.enderio.enderio.api.capacitor.CapacitorModifier;
import com.enderio.enderio.api.capacitor.QuadraticScalable;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.content.storage.fluid_tank.InternalTankTasks;
import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.attachment.ActionRange;
import com.enderio.enderio.foundation.attachment.FluidTankUser;
import com.enderio.enderio.foundation.attachment.RangedActor;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import com.enderio.enderio.foundation.block.entity.flags.CapacitorSupport;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.inventory.SingleSlotAccess;
import com.enderio.enderio.foundation.io.fluid.FluidItemInteractive;
import com.enderio.enderio.foundation.io.fluid.MachineFluidHandler;
import com.enderio.enderio.foundation.io.fluid.MachineFluidTank;
import com.enderio.enderio.foundation.io.fluid.MachineTankLayout;
import com.enderio.enderio.foundation.io.fluid.TankAccess;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.init.EIOBlockEntities;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class NiardBlockEntity extends PoweredMachineBlockEntity implements RangedActor, FluidItemInteractive, FluidTankUser {


    private static final QuadraticScalable ENERGY_CAPACITY = new QuadraticScalable(CapacitorModifier.ENERGY_CAPACITY,
        MachinesConfig.COMMON.ENERGY.NIARD_CAPACITY);
    private static final QuadraticScalable ENERGY_USAGE = new QuadraticScalable(CapacitorModifier.ENERGY_USE,
        MachinesConfig.COMMON.ENERGY.NIARD_USAGE);

    private static final ActionRange DEFAULT_RANGE = new ActionRange(5, false);
    private ActionRange actionRange = DEFAULT_RANGE;

    private NiardRangeIterator iterator;

    public static final  TankAccess TANK = new TankAccess();
    private final MachineFluidHandler fluidHandler;
    private static final int TANK_CAPACITY = 4 * FluidType.BUCKET_VOLUME;

    private static final int ENERGY_PER_BUCKET = 1_500;
    private static final int BASE_IDLE_TICKS = 40;

    public static final SingleSlotAccess FLUID_FILL_INPUT = new SingleSlotAccess();
    public static final SingleSlotAccess FLUID_FILL_OUTPUT = new SingleSlotAccess();


    public NiardBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(EIOBlockEntities.NIARD.get(), worldPosition, blockState, false, CapacitorSupport.REQUIRED,
            EnergyIOMode.Input, ENERGY_CAPACITY, ENERGY_USAGE);

        fluidHandler = createFluidHandler();
        iterator = new NiardRangeIterator(worldPosition, actionRange);
    }

    @Override
    public void serverTick() {
        if (isActive() && canAct()) {
            fillTank();
            tryPlaceFluid();
        }

        super.serverTick();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateMachineState(MachineState.EMPTY_TANK, TANK.isEmpty(this));
    }

    private void fillTank() {
        InternalTankTasks.fillInternal(this, TANK, FLUID_FILL_INPUT, FLUID_FILL_OUTPUT);
    }

    private void tryPlaceFluid() {
        var contents = TANK.getFluid(this);
        var energy = getEnergyStorage();
        int energyPerTick = getMaxEnergyUse();
        if (contents.getAmount() < FluidType.BUCKET_VOLUME || energy.getEnergyStored() < ENERGY_PER_BUCKET
            || energyPerTick <= 0) {
            return;
        }

        // A bucket has a fixed cost. Keep the average placement draw within the configured rate.
        int energyTicks = 1 + (ENERGY_PER_BUCKET - 1) / energyPerTick;
        int idleTicks = (int) (BASE_IDLE_TICKS / getCapacitorData().base());
        if (!canAct(Math.max(energyTicks, idleTicks))) {
            return;
        }

        Fluid fluid = contents.getFluid();
        BlockState fluidBlockState = fluid.defaultFluidState().createLegacyBlock();
        if (!fluidBlockState.getFluidState().isSource() || !fluid.isSame(fluidBlockState.getFluidState().getType())) {
            return;
        }

        int startIndex = iterator.getIndex();
        do {
            BlockPos pos = iterator.current();
            if (level.hasChunkAt(pos) && canPlaceFluid(pos, fluid)
                && level.setBlock(pos, fluidBlockState, Block.UPDATE_ALL)) {
                energy.takeEnergy(ENERGY_PER_BUCKET);
                TANK.drain(this, FluidType.BUCKET_VOLUME, IFluidHandler.FluidAction.EXECUTE);
                return;
            }
            iterator.moveToNextPosition();
        } while (iterator.getIndex() != startIndex);
    }

    @Override
    public void clientTick() {
        if (level.isClientSide && level instanceof ClientLevel clientLevel) {
            getActionRange().addClientParticle(clientLevel, getParticleLocation(),
                MachinesConfig.CLIENT.BLOCKS.NIARD_RANGE_COLOR.get());
        }

        super.clientTick();
    }

    @Override
    public boolean isActive() {
        return hasEnergy();
    }

    @Override
    public MachineTankLayout getTankLayout() {
        return new MachineTankLayout.Builder().tank(TANK, TANK_CAPACITY).build();
    }

    @Override
    public MachineInventoryLayout createInventoryLayout() {
        return MachineInventoryLayout.builder()
            .capacitor()
            .inputSlot((slot, stack) -> acceptItemDrain(stack))
            .slotAccess(FLUID_FILL_INPUT)
            .outputSlot()
            .slotAccess(FLUID_FILL_OUTPUT)
            .build();
    }

    @Override
    public MachineFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    @Override
    public MachineFluidHandler createFluidHandler() {
        return new MachineFluidHandler(this, getTankLayout()) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                super.onContentsChanged(slot);
                if (level != null) {
                    updateMachineState(MachineState.EMPTY_TANK, TANK.isEmpty(this));
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
                }
            }
        };
    }

    @Override
    public int getMaxRange() {
        return 7;
    }

    @Override
    public ActionRange getActionRange() {
        return actionRange;
    }

    @Override
    public void setActionRange(ActionRange actionRange) {
        applyActionRange(actionRange);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    private void applyActionRange(ActionRange actionRange) {
        this.actionRange = actionRange.clamp(0, getMaxRange());
        iterator = new NiardRangeIterator(getBlockPos(), this.actionRange);
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new NiardMenu(containerId, playerInventory, this);
    }

    public MachineFluidTank getFluidTank() {
        return TANK.getTank(this);
    }

    private BlockPos getParticleLocation() {
        return worldPosition.below(getRange() + 1);
    }

    private boolean canPlaceFluid(BlockPos pos, Fluid fluid) {
        BlockState state = level.getBlockState(pos);
        FluidState existing = state.getFluidState();
        if (fluid.isSame(existing.getType())) {
            return !existing.isSource();
        }
        return existing.isEmpty() && state.canBeReplaced();
    }

    public boolean acceptItemDrain(ItemStack item) {
        IFluidHandlerItem fluidHandlerCap = item.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
        return fluidHandlerCap != null;
    }

    // region Serialization

    @Override
    protected void readItemData(ItemStack components) {
        super.readItemData(components);

        FluidStack storedFluid = EIODataComponents.ITEM_FLUID_CONTENT.get(components);
        if (storedFluid != null) {
            var tank = TANK.getTank(this);
            tank.setFluid(storedFluid.copy());
        }

        var actionRange = EIODataComponents.ACTION_RANGE.get(components);
        if (actionRange != null) {
            applyActionRange(actionRange);
        }
    }

    @Override
    protected void writeItemData(ItemStack components) {
        super.writeItemData(components);

        var tank = TANK.getTank(this);
        if (!tank.isEmpty()) {
            EIODataComponents.ITEM_FLUID_CONTENT.set(components, tank.getFluid().copy());
        }

        EIODataComponents.ACTION_RANGE.set(components, actionRange);
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditional(tag);
        saveTank(lookupProvider, tag);
    }

    @Override
    protected void saveAdditionalSynced(CompoundTag tag) {
        var registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditionalSynced(tag);
        saveTank(registries, tag);

        if (!actionRange.equals(DEFAULT_RANGE)) {
            tag.put(MachineNBTKeys.ACTION_RANGE, actionRange.save(registries));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.load(tag);
        loadTank(lookupProvider, tag);

        if (tag.contains(MachineNBTKeys.ACTION_RANGE)) {
            applyActionRange(ActionRange.parse(lookupProvider, Objects.requireNonNull(tag.get(MachineNBTKeys.ACTION_RANGE))));
        } else {
            applyActionRange(DEFAULT_RANGE);
        }
    }

    @Override
    public void removeItemDataFromTag(CompoundTag tag) {
        super.removeItemDataFromTag(tag);
        tag.remove(MachineNBTKeys.ACTION_RANGE);
    }

    // endregion
}
