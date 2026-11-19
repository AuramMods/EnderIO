package com.enderio.enderio.content.machines.drain;

import com.enderio.core.annotations.UseOnly;
import com.enderio.enderio.api.capacitor.CapacitorModifier;
import com.enderio.enderio.api.capacitor.QuadraticScalable;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.attachment.ActionRange;
import com.enderio.enderio.foundation.attachment.FluidTankUser;
import com.enderio.enderio.foundation.attachment.RangedActor;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import com.enderio.enderio.foundation.block.entity.flags.CapacitorSupport;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.io.Forge6MachineItemData;
import com.enderio.enderio.foundation.io.IOConfig;
import com.enderio.enderio.foundation.io.LegacyMachineData;
import com.enderio.enderio.foundation.io.fluid.MachineFluidHandler;
import com.enderio.enderio.foundation.io.fluid.MachineFluidTank;
import com.enderio.enderio.foundation.io.fluid.MachineTankLayout;
import com.enderio.enderio.foundation.io.fluid.TankAccess;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.init.EIOBlockEntities;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DrainBlockEntity extends PoweredMachineBlockEntity implements RangedActor, FluidTankUser {
    public static final String CONSUMED = "Consumed";
    private static final QuadraticScalable ENERGY_CAPACITY = new QuadraticScalable(CapacitorModifier.ENERGY_CAPACITY,
            MachinesConfig.COMMON.ENERGY.DRAIN_CAPACITY);
    private static final QuadraticScalable ENERGY_USAGE = new QuadraticScalable(CapacitorModifier.ENERGY_USE,
            MachinesConfig.COMMON.ENERGY.DRAIN_USAGE);

    private static final ActionRange DEFAULT_RANGE = new ActionRange(5, false);

    private final MachineFluidHandler fluidHandler;
    private static final TankAccess TANK = new TankAccess();
    private static final int CAPACITY = 3 * FluidType.BUCKET_VOLUME;
    private static final int ENERGY_PER_BUCKET = 1_500;
    private List<BlockPos> positions;
    private int currentIndex = 0;
    private boolean collectedSource;
    private int consumed = 0;
    private Fluid type = Fluids.EMPTY;

    private ActionRange actionRange = DEFAULT_RANGE;

    public DrainBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(EIOBlockEntities.DRAIN.get(), worldPosition, blockState, false, CapacitorSupport.REQUIRED,
                EnergyIOMode.Input, ENERGY_CAPACITY, ENERGY_USAGE);
        fluidHandler = createFluidHandler();
    }

    @Override
    public ActionRange getActionRange() {
        return actionRange;
    }

    @Override
    @UseOnly(LogicalSide.SERVER)
    public void setActionRange(ActionRange actionRange) {
        applyActionRange(actionRange);
        setChanged();

        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    private void applyActionRange(ActionRange range) {
        int previousRange = getRange();
        actionRange = range.clamp(0, getMaxRange());
        if (previousRange != getRange() && level != null && !level.isClientSide()) {
            updateLocations();
        }
    }

    @Override
    public int getMaxRange() {
        return 10;
    }

    @Override
    public @Nullable MachineInventoryLayout createInventoryLayout() {
        return MachineInventoryLayout.builder().capacitor().build();
    }

    @Override
    public @Nullable MachineTankLayout getTankLayout() {
        return MachineTankLayout.builder().tank(TANK, CAPACITY, f -> type.isSame(f.getFluid())).build();
    }

    public MachineFluidTank getFluidTank() {
        return TANK.getTank(this);
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
                updateMachineState(MachineState.FULL_TANK, getFluidInTank(slot).getAmount() >= getTankCapacity(slot));
                setChanged();
            }
        };
    }

    @Override
    public void serverTick() {
        if (isActive()) {
            drainFluids();
        }

        super.serverTick();
    }

    @Override
    public IOConfig getDefaultIOConfig() {
        return IOConfig.of(IOMode.PUSH);
    }

    @Override
    public boolean isActive() {
        if (!canAct() || !isCapacitorInstalled()) {
            return false;
        }

        BlockState intake = getSource(worldPosition.below());
        updateMachineState(MachineState.NO_SOURCE, intake == null);
        type = intake == null ? Fluids.EMPTY : intake.getFluidState().getType();
        return intake != null && (hasEnergy() || consumed == ENERGY_PER_BUCKET)
            && TANK.fill(this, new FluidStack(type, FluidType.BUCKET_VOLUME),
                IFluidHandler.FluidAction.SIMULATE) == FluidType.BUCKET_VOLUME;
    }

    private void drainFluids() {
        BlockPos intake = worldPosition.below();
        int stop = Math.min(currentIndex + Math.max(1, getRange()), positions.size());
        for (; currentIndex < stop; currentIndex++) {
            BlockPos pos = positions.get(currentIndex);
            BlockState source = pos.equals(intake) ? null : getSource(pos);
            if (source == null || !type.isSame(source.getFluidState().getType())) {
                continue;
            }
            if (payForBucket()) {
                collectedSource |= collect(pos, source);
                currentIndex++;
            }
            return;
        }

        // Keep the intake until an entire pass finds no other source to collect.
        if (currentIndex == positions.size() && (collectedSource || payForBucket())) {
            if (!collectedSource) {
                collect(intake, level.getBlockState(intake));
            }
            currentIndex = 0;
            collectedSource = false;
        }
    }

    @Nullable
    private BlockState getSource(BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof BucketPickup && state.getFluidState().isSource() ? state : null;
    }

    private boolean payForBucket() {
        if (consumed == ENERGY_PER_BUCKET) {
            return true;
        }
        consumed += getEnergyStorage().consumeEnergy(ENERGY_PER_BUCKET - consumed, false);
        return false;
    }

    private boolean collect(BlockPos pos, BlockState source) {
        ((BucketPickup) source.getBlock()).pickupBlock(level, pos, source);
        var remaining = level.getFluidState(pos);
        if (remaining.isSource() && type.isSame(remaining.getType())) {
            return false;
        }
        consumed = 0;
        TANK.fill(this, new FluidStack(type, FluidType.BUCKET_VOLUME), IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    public BlockPos getParticleLocation() {
        return worldPosition.below(getRange() + 1);
    }

    @Override
    public void clientTick() {
        if (level instanceof ClientLevel clientLevel) {
            getActionRange().addClientParticle(clientLevel, getParticleLocation(),
                    MachinesConfig.CLIENT.BLOCKS.DRAIN_RANGE_COLOR.get());
        }

        super.clientTick();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!level.isClientSide()) {
            updateLocations();
        }
    }

    private void updateLocations() {
        positions = new ArrayList<>();
        currentIndex = 0;
        collectedSource = false;
        int range = getRange();
        for (BlockPos pos : BlockPos.betweenClosed(worldPosition.offset(-range, -range * 2 - 1, -range),
                worldPosition.offset(range, -1, range))) {
            positions.add(pos.immutable()); // Need to make it immutable
        }
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new DrainMenu(containerId, playerInventory, this);
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        var lookupProvider = level == null ? RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditional(tag);
        tag.putInt(CONSUMED, consumed);
        saveTank(lookupProvider, tag);
    }

    @Override
    protected void saveAdditionalSynced(CompoundTag tag) {
        var registries = level == null ? RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditionalSynced(tag);

        if (!actionRange.equals(DEFAULT_RANGE)) {
            tag.put(MachineNBTKeys.ACTION_RANGE, actionRange.save(registries));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        tag = LegacyMachineData.withNativeFields(tag);
        var lookupProvider = level == null ? RegistryAccess.EMPTY : level.registryAccess();
        super.load(tag);
        consumed = Mth.clamp(tag.getInt(CONSUMED), 0, ENERGY_PER_BUCKET);
        loadTank(lookupProvider, tag);

        var savedRange = tag.get(MachineNBTKeys.ACTION_RANGE);
        var range = savedRange == null ? Forge6MachineItemData.readActionRange(tag)
            : ActionRange.parse(lookupProvider, savedRange);
        applyActionRange(Objects.requireNonNullElse(range, DEFAULT_RANGE));
    }

    @Override
    protected void readItemData(ItemStack components) {
        super.readItemData(components);

        var actionRange = EIODataComponents.ACTION_RANGE.get(components);
        if (actionRange != null) {
            applyActionRange(actionRange);
        }

        FluidStack storedFluid = EIODataComponents.ITEM_FLUID_CONTENT.get(components);
        if (storedFluid != null) {
            var tank = TANK.getTank(this);
            tank.setFluid(storedFluid.copy());
        }
    }

    @Override
    protected void writeItemData(ItemStack components) {
        super.writeItemData(components);

        // Only if unchanged.
        if (!actionRange.equals(DEFAULT_RANGE)) {
            EIODataComponents.ACTION_RANGE.set(components, actionRange);
        }

        var tank = TANK.getTank(this);
        if (!tank.isEmpty()) {
            EIODataComponents.ITEM_FLUID_CONTENT.set(components, tank.getFluid().copy());
        }
    }

    @Override
    public void removeItemDataFromTag(CompoundTag tag) {
        super.removeItemDataFromTag(tag);
        tag.remove(MachineNBTKeys.ACTION_RANGE);
        tag.remove(CONSUMED);
    }
}
