package com.enderio.modded_conduits;

import mekanism.api.Action;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.api.chemical.pigment.IPigmentHandler;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.chemical.slurry.ISlurryHandler;
import mekanism.api.chemical.slurry.Slurry;
import mekanism.api.chemical.slurry.SlurryStack;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;

import java.util.function.BiFunction;

/** A real Mekanism capability whose destination may accept less at execution. */
abstract class ChangingChemicalTank<C extends Chemical<C>, S extends ChemicalStack<C>> implements IChemicalHandler<C, S> {
    private final BiFunction<S, Long, S> withAmount;
    private S stored;
    boolean outputOnly;
    long executionLimit = Long.MAX_VALUE;

    private ChangingChemicalTank(S empty, BiFunction<S, Long, S> withAmount) {
        stored = empty;
        this.withAmount = withAmount;
    }

    static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (!(event.getObject() instanceof BarrelBlockEntity)) return;
        var gas = LazyOptional.of(GasTank::new);
        var infusion = LazyOptional.of(InfusionTank::new);
        var pigment = LazyOptional.of(PigmentTank::new);
        var slurry = LazyOptional.of(SlurryTank::new);
        event.addCapability(ResourceLocation.fromNamespaceAndPath("enderio_modded_conduits_tests", "changing_chemical"),
            new ICapabilityProvider() {
                @Override
                public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                    if (cap == Capabilities.GAS_HANDLER) return gas.cast();
                    if (cap == Capabilities.INFUSION_HANDLER) return infusion.cast();
                    if (cap == Capabilities.PIGMENT_HANDLER) return pigment.cast();
                    if (cap == Capabilities.SLURRY_HANDLER) return slurry.cast();
                    return LazyOptional.empty();
                }
            });
        event.addListener(() -> {
            gas.invalidate();
            infusion.invalidate();
            pigment.invalidate();
            slurry.invalidate();
        });
    }

    @Override public int getTanks() { return 1; }
    @Override public S getChemicalInTank(int tank) { return stored; }
    @Override public void setChemicalInTank(int tank, S stack) { stored = stack; }
    @Override public long getTankCapacity(int tank) { return Long.MAX_VALUE; }
    @Override public boolean isValid(int tank, S stack) { return !outputOnly; }

    @Override
    public S insertChemical(int tank, S stack, Action action) {
        if (!isValid(tank, stack) || (!stored.isEmpty() && stored.getType() != stack.getType())) return stack;
        long inserted = Math.min(stack.getAmount(), getTankCapacity(tank) - stored.getAmount());
        if (action.execute()) {
            inserted = Math.min(inserted, executionLimit);
            if (inserted > 0) stored = withAmount.apply(stack, stored.getAmount() + inserted);
        }
        return inserted == stack.getAmount() ? getEmptyStack() : withAmount.apply(stack, stack.getAmount() - inserted);
    }

    @Override
    public S extractChemical(int tank, long amount, Action action) {
        long extracted = Math.min(amount, stored.getAmount());
        if (extracted == 0) return getEmptyStack();
        var result = withAmount.apply(stored, extracted);
        if (action.execute()) {
            stored = extracted == stored.getAmount() ? getEmptyStack() : withAmount.apply(stored, stored.getAmount() - extracted);
        }
        return result;
    }

    static final class GasTank extends ChangingChemicalTank<Gas, GasStack> implements IGasHandler {
        GasTank() { super(GasStack.EMPTY, GasStack::new); }
    }
    static final class InfusionTank extends ChangingChemicalTank<InfuseType, InfusionStack> implements IInfusionHandler {
        InfusionTank() { super(InfusionStack.EMPTY, InfusionStack::new); }
    }
    static final class PigmentTank extends ChangingChemicalTank<Pigment, PigmentStack> implements IPigmentHandler {
        PigmentTank() { super(PigmentStack.EMPTY, PigmentStack::new); }
    }
    static final class SlurryTank extends ChangingChemicalTank<Slurry, SlurryStack> implements ISlurryHandler {
        SlurryTank() { super(SlurryStack.EMPTY, SlurryStack::new); }
    }
}
