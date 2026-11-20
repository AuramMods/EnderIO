package com.enderio.enderio.gametests.conduits.fluid;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/** Native attached capability fixture: simulation is optimistic, execution may accept less. */
public final class ChangingFluidHandler implements IFluidHandler, ICapabilitySerializable<CompoundTag> {
    private final FluidTank tank = new FluidTank(32000);
    private final LazyOptional<IFluidHandler> capability = LazyOptional.of(() -> this);
    public boolean outputOnly;
    public int executionLimit = Integer.MAX_VALUE;

    public static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (event.getObject() instanceof BarrelBlockEntity) {
            var handler = new ChangingFluidHandler();
            event.addCapability(ResourceLocation.fromNamespaceAndPath("enderio_tests", "changing_fluid"), handler);
            event.addListener(handler.capability::invalidate);
        }
    }

    public void setFluid(FluidStack fluid) { tank.setFluid(fluid.copy()); }
    void setCapacity(int capacity) { tank.setCapacity(capacity); }
    public int amount() { return tank.getFluidAmount(); }
    @Override public int getTanks() { return 1; }
    @Override public FluidStack getFluidInTank(int index) { return tank.getFluidInTank(index); }
    @Override public int getTankCapacity(int index) { return tank.getTankCapacity(index); }
    @Override public boolean isFluidValid(int index, FluidStack fluid) { return !outputOnly; }
    @Override public int fill(FluidStack fluid, FluidAction action) {
        if (outputOnly) return 0;
        var offered = fluid.copy();
        if (action.execute()) offered.setAmount(Math.min(offered.getAmount(), executionLimit));
        return tank.fill(offered, action);
    }
    @Override public FluidStack drain(FluidStack fluid, FluidAction action) { return tank.drain(fluid, action); }
    @Override public FluidStack drain(int amount, FluidAction action) { return tank.drain(amount, action); }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> type, @Nullable Direction side) {
        return type == ForgeCapabilities.FLUID_HANDLER ? capability.cast() : LazyOptional.empty();
    }
    @Override public CompoundTag serializeNBT() {
        var tag = tank.writeToNBT(new CompoundTag());
        tag.putBoolean("OutputOnly", outputOnly);
        tag.putInt("ExecutionLimit", executionLimit);
        return tag;
    }
    @Override public void deserializeNBT(CompoundTag tag) {
        tank.readFromNBT(tag);
        outputOnly = tag.getBoolean("OutputOnly");
        executionLimit = tag.getInt("ExecutionLimit");
    }
}
