package com.enderio.enderio.content.machines.stirling_generator;

import com.enderio.enderio.api.capacitor.CapacitorModifier;
import com.enderio.enderio.api.capacitor.FixedScalable;
import com.enderio.enderio.api.capacitor.LinearScalable;
import com.enderio.enderio.api.capacitor.QuadraticScalable;
import com.enderio.enderio.api.capacitor.SteppedScalable;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.client.SoundHandler;
import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.foundation.MachineNBTKeys;
import com.enderio.enderio.foundation.block.ProgressMachineBlock;
import com.enderio.enderio.foundation.block.entity.PoweredMachineBlockEntity;
import com.enderio.enderio.foundation.block.entity.flags.CapacitorSupport;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.inventory.SingleSlotAccess;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.init.EIOBlockEntities;
import com.enderio.enderio.init.EIOSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.Nullable;

public class StirlingGeneratorBlockEntity extends PoweredMachineBlockEntity {

    public static final QuadraticScalable CAPACITY = new QuadraticScalable(CapacitorModifier.ENERGY_CAPACITY,
            MachinesConfig.COMMON.ENERGY.STIRLING_GENERATOR_CAPACITY);

    public static final SteppedScalable FUEL_EFFICIENCY = new SteppedScalable(CapacitorModifier.FUEL_EFFICIENCY,
            MachinesConfig.COMMON.ENERGY.STIRLING_GENERATOR_FUEL_EFFICIENCY_BASE,
            MachinesConfig.COMMON.ENERGY.STIRLING_GENERATOR_FUEL_EFFICIENCY_STEP);

    public static final LinearScalable GENERATION_SPEED = new LinearScalable(
            CapacitorModifier.BURNING_ENERGY_GENERATION, MachinesConfig.COMMON.ENERGY.STIRLING_GENERATOR_PRODUCTION);

    public static final SingleSlotAccess FUEL = new SingleSlotAccess();

    private int burnTime;
    private int burnDuration;

    public StirlingGeneratorBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(EIOBlockEntities.STIRLING_GENERATOR.get(), worldPosition, blockState, true, CapacitorSupport.REQUIRED,
                EnergyIOMode.Output, CAPACITY, FixedScalable.ZERO);
    }

    public int getGenerationRate() {
        return GENERATION_SPEED.scaleI(this::getCapacitorData).get();
    }

    public int getFuelEfficiency() {
        return FUEL_EFFICIENCY.scaleI(this::getCapacitorData).get();
    }

    @Override
    public MachineInventoryLayout createInventoryLayout() {
        return MachineInventoryLayout.builder()
                .inputSlot((slot, stack) -> getBurnTime(stack) > 0)
                .slotAccess(FUEL)
                .capacitor()
                .build();
    }

    @Override
    public void serverTick() {
        // A lit fuel continues burning even when redstone stops new fuel.
        if (isGenerating()) {
            burnTime--;
            int energyAdded = 0;
            if (!requiresCapacitor() || isCapacitorInstalled()) {
                energyAdded = getEnergyStorage().addEnergy(getGenerationRate());
            }
            if (energyAdded == 0) {
                // The burn timer still needs saving when the energy buffer cannot accept anything.
                setChanged();
            }
            if (burnTime == 0) {
                updateFuelState();
            }
        }

        if (canAct() && !isGenerating() && getEnergyStorage().getEnergyStored() < getEnergyStorage().getMaxEnergyStored()) {
            igniteFuel();
        }

        super.serverTick();
    }

    private static int getBurnTime(ItemStack fuel) {
        return fuel.isEmpty() || !fuel.getCraftingRemainingItem().isEmpty()
            ? 0 : ForgeHooks.getBurnTime(fuel, RecipeType.SMELTING);
    }

    private void igniteFuel() {
        ItemStack fuel = FUEL.getItemStack(this);
        int furnaceTicks = getBurnTime(fuel);
        if (furnaceTicks <= 0) {
            return;
        }
        float burnSpeed = MachinesConfig.COMMON.ENERGY.STIRLING_GENERATOR_BURN_SPEED.get().floatValue();
        float efficiency = getFuelEfficiency() / 100.0f;
        int duration = (int) Math.floor(furnaceTicks * burnSpeed * efficiency);
        if (duration <= 0) {
            return;
        }
        burnTime = duration;
        burnDuration = duration;
        FUEL.setStackInSlot(this, fuel.copyWithCount(fuel.getCount() - 1));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(ProgressMachineBlock.POWERED)) {
            double x = pos.getX() + 0.5;
            double y = pos.getY();
            double z = pos.getZ() + 0.5;

            SoundHandler.playSound(pos, EIOSounds.STIRLING.get(), SoundSource.BLOCKS, MachinesConfig.CLIENT.MACHINE_VOLUME.get(), 1.0f, random, x, y, z);


            Direction direction = state.getValue(ProgressMachineBlock.FACING);
            Direction.Axis axis = direction.getAxis();
            double r = 0.52;
            double ss = random.nextDouble() * 0.6 - 0.3;
            double dx = axis == Direction.Axis.X ? direction.getStepX() * r : ss;
            double dy = random.nextDouble() * 6.0 / 16.0;
            double dz = axis == Direction.Axis.Z ? direction.getStepZ() * r : ss;
            level.addParticle(ParticleTypes.SMOKE, x + dx, y + dy, z + dz, 0.0, 0.0, 0.0);
            level.addParticle(ParticleTypes.FLAME, x + dx, y + dy, z + dz, 0.0, 0.0, 0.0);
        } else {
            SoundHandler.stopSound(pos);
        }
    }

    @Override
    public boolean isActive() {
        return canAct() && isGenerating();
    }

    public boolean isGenerating() {
        if (level == null) {
            return false;
        }

        return burnTime > 0;
    }

    public float getBurnProgress() {
        if (burnDuration != 0) {
            return burnTime / (float) burnDuration;
        }

        return 0;
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new StirlingGeneratorMenu(containerId, inventory, this);
    }

    @Override
    protected void onInventoryContentsChanged(int slot) {
        super.onInventoryContentsChanged(slot);
        if (FUEL.isSlot(slot)) {
            updateFuelState();
        }
    }

    private void updateFuelState() {
        updateMachineState(MachineState.EMPTY_INPUT, burnTime == 0 && FUEL.getItemStack(this).isEmpty());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        if (tag.contains(MachineNBTKeys.BURN_TIME, CompoundTag.TAG_INT)) {
            burnTime = tag.getInt(MachineNBTKeys.BURN_TIME);
        }

        if (tag.contains(MachineNBTKeys.BURN_DURATION, CompoundTag.TAG_INT)) {
            burnDuration = tag.getInt(MachineNBTKeys.BURN_DURATION);
        }

        updatePowerState();
        updateFuelState();
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        tag.putInt(MachineNBTKeys.BURN_TIME, burnTime);
        tag.putInt(MachineNBTKeys.BURN_DURATION, burnDuration);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);

        updateFuelState();
    }

    @Override
    protected void updatePowerState() {
        updateMachineState(MachineState.NO_POWER, false);
        boolean full = isCapacitorInstalled() && getEnergyStorage().getEnergyStored() >= getEnergyStorage().getMaxEnergyStored();
        if (getMachineStates().contains(MachineState.FULL_POWER) != full) {
            updateMachineState(MachineState.FULL_POWER, full);
        }
    }
}
