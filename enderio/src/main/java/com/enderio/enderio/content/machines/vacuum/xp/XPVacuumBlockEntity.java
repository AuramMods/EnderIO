package com.enderio.enderio.content.machines.vacuum.xp;

import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.content.machines.vacuum.VacuumMachineBlockEntity;
import com.enderio.enderio.foundation.attachment.FluidTankUser;
import com.enderio.enderio.foundation.io.fluid.MachineFluidHandler;
import com.enderio.enderio.foundation.io.fluid.MachineFluidTank;
import com.enderio.enderio.foundation.io.fluid.MachineTankLayout;
import com.enderio.enderio.foundation.io.fluid.TankAccess;
import com.enderio.enderio.init.EIOBlockEntities;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import static com.enderio.enderio.foundation.util.ExperienceUtil.EXP_TO_FLUID;

public class XPVacuumBlockEntity extends VacuumMachineBlockEntity<ExperienceOrb> implements FluidTankUser {

    private final MachineFluidHandler fluidHandler;
    private static final TankAccess TANK = new TankAccess();

    public XPVacuumBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(EIOBlockEntities.XP_VACUUM.get(), worldPosition, blockState, ExperienceOrb.class);
        fluidHandler = createFluidHandler();
    }

    @Override
    public String getColor() {
        return MachinesConfig.CLIENT.BLOCKS.XP_VACUUM_RANGE_COLOR.get();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new XPVacuumMenu(containerId, inventory, this);
    }

    @Override
    public void handleEntity(ExperienceOrb orb) {
        var tank = getFluidTank();
        var stored = tank.getFluid();
        var xpJuice = EIOFluids.XP_JUICE.source().get();
        int value = orb.getValue();
        int orbCount = orb.count;
        int space = tank.getCapacity() - tank.getFluidAmount();
        if (value <= 0 || orbCount <= 0 || space < EXP_TO_FLUID || !stored.isEmpty() && stored.getFluid() != xpJuice) {
            return;
        }

        int points = (int) Math.min((long) value * orbCount, space / EXP_TO_FLUID);
        int remainingOrbs = orbCount - points / value;
        int partial = points % value;
        ExperienceOrb remainder = null;
        if (partial > 0 && remainingOrbs > 1) {
            // The untouched orbs must keep their original value. Split off the partial one.
            remainder = createRemainder(orb, value - partial);
            if (orb.level().addFreshEntity(remainder)) {
                remainingOrbs--;
            } else {
                remainder = null;
                points -= partial;
                partial = 0;
            }
        }
        if (points == 0) {
            return;
        }

        int volume = points * EXP_TO_FLUID;
        var deposit = stored.isEmpty() ? new FluidStack(xpJuice, volume) : new FluidStack(stored, volume);
        if (orb.isRemoved() || orb.count != orbCount || orb.getValue() != value || tank.fill(deposit, FluidAction.SIMULATE) != volume) {
            if (remainder != null) remainder.discard();
            return;
        }
        tank.fill(deposit, FluidAction.EXECUTE);
        if (remainingOrbs == 0) {
            orb.discard();
        } else {
            orb.count = remainingOrbs;
            if (remainder == null) orb.value -= partial;
        }
    }

    private static ExperienceOrb createRemainder(ExperienceOrb original, int value) {
        var remainder = new ExperienceOrb(original.level(), original.getX(), original.getY(), original.getZ(), value);
        var saved = new CompoundTag();
        original.addAdditionalSaveData(saved);
        saved.putShort("Value", (short) value);
        saved.putInt("Count", 1);
        remainder.readAdditionalSaveData(saved);
        remainder.setDeltaMovement(original.getDeltaMovement());
        remainder.setNoGravity(original.isNoGravity());
        return remainder;
    }

    // region Fluid Storage

    @Override
    public MachineTankLayout getTankLayout() {
        return new MachineTankLayout.Builder().tank(TANK, Integer.MAX_VALUE).build();
    }

    @Override
    public MachineFluidHandler createFluidHandler() {
        return new MachineFluidHandler(this, getTankLayout()) {
            @Override
            protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                setChanged();
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

    // endregion

    // region Serialization

    @Override
    protected void readItemData(ItemStack components) {
        super.readItemData(components);

        FluidStack storedFluid = EIODataComponents.ITEM_FLUID_CONTENT.get(components);
        if (storedFluid != null) {
            var tank = TANK.getTank(this);
            tank.setFluid(storedFluid.copy());
        }
    }

    @Override
    protected void writeItemData(ItemStack components) {
        super.writeItemData(components);

        var tank = TANK.getTank(this);
        if (!tank.isEmpty()) {
            EIODataComponents.ITEM_FLUID_CONTENT.set(components, tank.getFluid().copy());
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.saveAdditional(tag);
        saveTank(lookupProvider, tag);
    }

    @Override
    public void load(CompoundTag tag) {
        var lookupProvider = level == null ? net.minecraft.core.RegistryAccess.EMPTY : level.registryAccess();
        super.load(tag);
        loadTank(lookupProvider, tag);
    }

    // endregion
}
