package com.enderio.enderio.content.machines.vacuum.xp;

import net.minecraft.world.item.ItemStack;
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
    public void handleEntity(ExperienceOrb xpe) {
        int filled = TANK.fill(this, new FluidStack(EIOFluids.XP_JUICE.source().get(), xpe.getValue() * EXP_TO_FLUID),
                FluidAction.EXECUTE);
        if (filled == xpe.getValue() * EXP_TO_FLUID) {
            xpe.discard();
        } else {
            xpe.value -= Math.round(filled / ((float) EXP_TO_FLUID));
        }
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
