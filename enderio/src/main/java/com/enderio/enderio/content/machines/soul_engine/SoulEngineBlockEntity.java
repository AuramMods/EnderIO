package com.enderio.enderio.content.machines.soul_engine;

import com.enderio.enderio.integration.gamestages.StageFeature;
import com.enderio.core.common.capability.EnderFluidUtil;
import net.minecraft.world.item.ItemStack;
import com.enderio.enderio.api.capacitor.CapacitorModifier;
import com.enderio.enderio.api.capacitor.FixedScalable;
import com.enderio.enderio.api.capacitor.LinearScalable;
import com.enderio.enderio.api.capacitor.QuadraticScalable;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.api.soul.binding.SoulBindable;
import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.attachment.FluidTankUser;
import com.enderio.enderio.foundation.block.ProgressMachineBlock;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import com.enderio.enderio.foundation.block.entity.flags.CapacitorSupport;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.io.fluid.MachineFluidHandler;
import com.enderio.enderio.foundation.io.fluid.MachineFluidTank;
import com.enderio.enderio.foundation.io.fluid.MachineTankLayout;
import com.enderio.enderio.foundation.io.fluid.TankAccess;
import com.enderio.enderio.foundation.soul.Forge6SoulData;
import com.enderio.enderio.foundation.souldata.EngineSoul;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.init.EIOBlockEntities;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public class SoulEngineBlockEntity extends PoweredMachineBlockEntity implements FluidTankUser, SoulBindable {

    @Override
    public StageFeature automationStageFeature() {
        return StageFeature.SOUL_DEVICE;
    }


    private static final QuadraticScalable CAPACITY = new QuadraticScalable(CapacitorModifier.ENERGY_CAPACITY,
            MachinesConfig.COMMON.ENERGY.SOUL_ENGINE_CAPACITY);
    public static final LinearScalable BURN_SPEED = new LinearScalable(CapacitorModifier.FIXED,
            MachinesConfig.COMMON.ENERGY.SOUL_ENGINE_BURN_SPEED);
    // TODO capacitor increase efficiency
    public static final LinearScalable GENERATION_SPEED = new LinearScalable(CapacitorModifier.FIXED, () -> 1);

    private static final String BURNED_TICKS = "BurnedTicks";
    private Soul boundSoul = Soul.EMPTY;
    public static final int FLUID_CAPACITY = 2 * FluidType.BUCKET_VOLUME;
    private final MachineFluidHandler fluidHandler;
    private static final TankAccess TANK = new TankAccess();
    private int burnedTicks = 0;

    public SoulEngineBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(EIOBlockEntities.SOUL_ENGINE.get(), worldPosition, blockState, true, CapacitorSupport.REQUIRED,
                EnergyIOMode.Output, CAPACITY, FixedScalable.ZERO);
        fluidHandler = createFluidHandler();
    }

    @Override
    public MachineInventoryLayout createInventoryLayout() {
        return MachineInventoryLayout.builder().capacitor().build();
    }

    @Override
    public void serverTick() {
        var rule = getSoulData();
        if (rule != null) {
            int energy = energyPerMillibucket(rule);
            if (canBurn(rule, energy)) producePower(rule, energy);
        }
        super.serverTick();
        updateStatus(MachineState.NOT_SOULBOUND, rule == null);
        updateStatus(MachineState.EMPTY_TANK, TANK.isEmpty(this));
        updateStatus(MachineState.FULL_POWER, rule != null && isCapacitorInstalled()
                && !hasRoomFor(energyPerMillibucket(rule)));
    }

    private void updateStatus(MachineState state, boolean active) {
        if (getMachineStates().contains(state) != active) updateMachineState(state, active);
    }

    @Override
    protected void updatePowerState() {
        updateMachineState(MachineState.NO_POWER, false);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(ProgressMachineBlock.POWERED)) {
            double x = pos.getX() + 0.5;
            double y = pos.getY();
            double z = pos.getZ() + 0.5;

            Direction direction = state.getValue(ProgressMachineBlock.FACING);
            Direction.Axis axis = direction.getAxis();
            double r = 0.52;
            double ss = random.nextDouble() * 0.6 - 0.3;
            double dx = axis == Direction.Axis.X ? direction.getStepX() * r : ss;
            double dy = random.nextDouble() * 6.0 / 16.0;
            double dz = axis == Direction.Axis.Z ? direction.getStepZ() * r : ss;
            level.addParticle(ParticleTypes.SMOKE, x + dx, y + dy, z + dz, 0.0, 0.0, 0.0);
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + dx, y + dy, z + dz, 0.0, 0.0, 0.0);
        }
    }

    @Nullable
    public EntityType<?> getEntityType() {
        return boundSoul.hasEntity() ? boundSoul.entityType() : null;
    }

    @Override
    public Soul getBoundSoul() {
        return boundSoul;
    }

    @Override
    public boolean canBind() {
        return true;
    }

    @Override
    public boolean isSoulValid(Soul soul) {
        return soul.isEmpty() || EngineSoul.ENGINE.matches(level, soul.entityTypeId()).isPresent();
    }

    @Override
    public void bindSoul(Soul newSoul) {
        if (!isSoulValid(newSoul)) throw new IllegalArgumentException("No Soul Engine recipe for " + newSoul.entityTypeId());
        if (boundSoul.equals(newSoul)) return;
        if (boundSoul.entityType() != newSoul.entityType()) burnedTicks = 0;
        this.boundSoul = newSoul;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public boolean isActive() {
        var rule = getSoulData();
        return rule != null && canBurn(rule, energyPerMillibucket(rule));
    }

    private @Nullable EngineSoul.SoulData getSoulData() {
        return boundSoul.hasEntity() ? EngineSoul.ENGINE.matches(level, boundSoul.entityTypeId()).orElse(null) : null;
    }

    private boolean hasRoomFor(int energy) {
        return getEnergyStorage().addEnergy(energy, true) == energy;
    }

    private boolean canBurn(EngineSoul.SoulData rule, int energy) {
        return canAct() && isCapacitorInstalled() && matchesFuel(rule, TANK.getFluid(this)) && hasRoomFor(energy);
    }

    private void producePower(EngineSoul.SoulData rule, int energy) {
        if (burnedTicks < rule.tickpermb()) {
            burnedTicks = (int) Math.min(Integer.MAX_VALUE, (long) burnedTicks + getBurnRate());
        }
        if (burnedTicks >= rule.tickpermb()) {
            burnedTicks -= rule.tickpermb();
            TANK.drain(this, 1, IFluidHandler.FluidAction.EXECUTE);
            getEnergyStorage().addEnergy(energy);
        } else if (level != null) {
            // A partially paid burn changes the save without changing any comparator output.
            level.blockEntityChanged(worldPosition);
        }
    }

    public int getBurnRate() {
        return BURN_SPEED.scaleI(this::getCapacitorData).get();
    }

    public float getGenerationRate() {
        return MachinesConfig.COMMON.ENERGY.SOUL_ENGINE_GENERATION.get().floatValue();
    }

    public int getEnergyPerMillibucket() {
        var rule = getSoulData();
        return rule == null ? 0 : energyPerMillibucket(rule);
    }

    private int energyPerMillibucket(EngineSoul.SoulData rule) {
        return (int) (rule.powerpermb() * getGenerationRate());
    }

    @Override
    public MachineTankLayout getTankLayout() {
        return MachineTankLayout.builder().tank(TANK, FLUID_CAPACITY, this::isFluidValid).build();
    }

    public MachineFluidHandler createFluidHandler() {
        return new MachineFluidHandler(this, getTankLayout()) {
            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                updateStatus(MachineState.EMPTY_TANK, TANK.isEmpty(SoulEngineBlockEntity.this));
                setChanged();
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                // Convert into tagged fluid
                if (TANK.isFluidValid(this, resource)) {
                    FluidStack currentFluid = TANK.getFluid(this);
                    if (currentFluid.isEmpty() || resource.isFluidEqual(currentFluid)) {
                        return super.fill(resource, action);
                    } else {
                        return super.fill(EnderFluidUtil.copyWithAmount(currentFluid, resource.getAmount()), action);
                    }
                }

                // Non-tagged fluid.
                return 0;
            }
        };
    }

    public MachineFluidTank getFluidTank() {
        return TANK.getTank(this);
    }

    @Override
    public MachineFluidHandler getFluidHandler() {
        return fluidHandler;
    }

    private boolean isFluidValid(FluidStack fuel) {
        var rule = getSoulData();
        return rule != null && matchesFuel(rule, fuel);
    }

    private static boolean matchesFuel(EngineSoul.SoulData rule, FluidStack fuel) {
        return !fuel.isEmpty() && rule.matchesFuel(fuel.getFluid());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SoulEngineMenu(containerId, playerInventory, this);
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditional(tag);
        tag.putInt(BURNED_TICKS, burnedTicks);
        saveTank(lookupProvider, tag);
    }

    @Override
    protected void saveAdditionalSynced(CompoundTag tag) {
        var registries = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditionalSynced(tag);
        tag.put(MachineNBTKeys.ENTITY_STORAGE, boundSoul.saveOptional(registries));
    }

    @Override
    public void load(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.load(tag);
        burnedTicks = Math.max(0, tag.getInt(BURNED_TICKS));
        var storage = tag.getCompound(MachineNBTKeys.ENTITY_STORAGE);
        boundSoul = Forge6SoulData.isLegacyStorage(storage) ? Forge6SoulData.read(storage) : Soul.parseOptional(lookupProvider, storage);
        loadTank(lookupProvider, tag);
    }

    @Override
    protected void readItemData(ItemStack components) {
        super.readItemData(components);
        boundSoul = EIODataComponents.SOUL.getOrDefault(components, Soul.EMPTY);

        FluidStack storedFluid = EIODataComponents.ITEM_FLUID_CONTENT.get(components);
        if (storedFluid != null) {
            var tank = TANK.getTank(this);
            tank.setFluid(storedFluid.copy());
        }
    }

    @Override
    protected void writeItemData(ItemStack components) {
        super.writeItemData(components);

        if (boundSoul.hasEntity()) {
            EIODataComponents.SOUL.set(components, boundSoul);
        }

        var tank = TANK.getTank(this);
        if (!tank.isEmpty()) {
            EIODataComponents.ITEM_FLUID_CONTENT.set(components, tank.getFluid().copy());
        }
    }

    @Override
    public void removeItemDataFromTag(CompoundTag tag) {
        super.removeItemDataFromTag(tag);
        tag.remove(MachineNBTKeys.ENTITY_STORAGE);
    }
}
