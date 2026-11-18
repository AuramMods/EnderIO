package com.enderio.enderio.foundation.io.fluid;

import com.enderio.core.CoreNBTKeys;
import com.enderio.core.common.serialization.StackDataCodecs;
import com.enderio.enderio.api.io.IOConfigurable;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * MachineFluidStorage takes a list of fluid tanks and handles IO for them all.
 */
public class MachineFluidHandler implements IFluidHandler, INBTSerializable<CompoundTag> {

    public static final String TANK_INDEX = "Index";
    public static final String TANK_CONTENTS = "Contents";
    private final IOConfigurable config;
    private final MachineTankLayout layout;
    private final Map<Integer, MachineFluidTank> tanks = new HashMap<>();
    private final List<FluidStack> stacks;
    private ListTag overflowTanks = new ListTag();

    // Not sure if we need this but might be useful to update recipe/task if tank is
    // filled.
    private IntConsumer changeListener = i -> {
    };

    public MachineFluidHandler(IOConfigurable config, MachineTankLayout layout) {
        this.config = config;
        this.layout = layout;
        this.stacks = NonNullList.withSize(getTanks(), FluidStack.EMPTY);
    }

    public void addSlotChangedCallback(IntConsumer callback) {
        changeListener = changeListener.andThen(callback);
    }

    public final IOConfigurable getConfig() {
        return config;
    }

    public MachineTankLayout getLayout() {
        return layout;
    }

    // Not a good idea to use this method. Tank Access should be the way to access
    // tanks
    @Deprecated
    public final MachineFluidTank getTank(int tank) {
        if (tank < 0 || tank >= getTanks()) {
            throw new IndexOutOfBoundsException("No tank found for index " + tank + " in range" + getTanks() + ".");
        }
        return tanks.computeIfAbsent(tank, i -> new MachineFluidTank(i, this));
    }

    @Override
    public int getTanks() {
        return layout.getTankCount();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return stacks.get(tank);
    }

    public void setFluidInTank(int tank, FluidStack fluid) {
        var stored = fluid.isEmpty() ? FluidStack.EMPTY : fluid.copy();
        if (!StackDataCodecs.fluidsEqual(stacks.get(tank), stored)) {
            stacks.set(tank, stored);
            onContentsChanged(tank);
            changeListener.accept(tank);
        }
    }

    @Override
    public int getTankCapacity(int tank) {
        return layout.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return layout.isFluidValid(tank, stack);
    }

    public boolean canInsert(int tank) {
        return layout.canInsert(tank);
    }

    public boolean canExtract(int tank) {
        return layout.canExtract(tank);
    }

    @Nullable
    public IFluidHandler getForSide(@Nullable Direction side) {
        if (side == null) {
            return this;
        }

        if (config.getIOMode(side).canConnect()) {
            return new Sided(this, side);
        }

        return null;
    }

    public int fill(int tank, FluidStack resource, FluidAction action) {
        var current = getFluidInTank(tank);
        if (resource.isEmpty() || !isFluidValid(tank, resource)
                || !current.isEmpty() && !current.isFluidEqual(resource)) {
            return 0;
        }
        int accepted = Math.min(resource.getAmount(), Math.max(0, getTankCapacity(tank) - current.getAmount()));
        if (action.execute() && accepted > 0) {
            setFluidInTank(tank, new FluidStack(resource, current.getAmount() + accepted));
        }
        return accepted;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        var remaining = resource.copy();
        int filled = 0;
        for (int tank = 0; tank < getTanks() && !remaining.isEmpty(); tank++) {
            if (canInsert(tank)) {
                int accepted = fill(tank, remaining, action);
                remaining.shrink(accepted);
                filled += accepted;
            }
        }
        return filled;
    }

    public FluidStack drain(int tank, int maxDrain, FluidAction action) {
        var current = getFluidInTank(tank);
        if (maxDrain <= 0 || current.isEmpty()) {
            return FluidStack.EMPTY;
        }
        int amount = Math.min(maxDrain, current.getAmount());
        var drained = new FluidStack(current, amount);
        if (action.execute()) {
            setFluidInTank(tank, new FluidStack(current, current.getAmount() - amount));
        }
        return drained;
    }

    public FluidStack drain(int tank, FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !getFluidInTank(tank).isFluidEqual(resource)) {
            return FluidStack.EMPTY;
        }
        return drain(tank, resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        for (int tank = 0; tank < getTanks(); tank++) {
            if (canExtract(tank)) {
                var drained = drain(tank, resource, action);
                if (!drained.isEmpty()) {
                    return drained;
                }
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        for (int tank = 0; tank < getTanks(); tank++) {
            if (canExtract(tank)) {
                var drained = drain(tank, maxDrain, action);
                if (!drained.isEmpty()) {
                    return drained;
                }
            }
        }
        return FluidStack.EMPTY;
    }

    protected void onContentsChanged(int slot) {
    }

    @Override
    public CompoundTag serializeNBT() {
        var tanks = new ListTag();
        for (int tank = 0; tank < getTanks(); tank++) {
            var entry = new CompoundTag();
            entry.putInt(TANK_INDEX, tank);
            entry.put(TANK_CONTENTS, StackDataCodecs.saveFluid(stacks.get(tank)));
            tanks.add(entry);
        }
        overflowTanks.forEach(tank -> tanks.add(tank.copy()));
        var tag = new CompoundTag();
        tag.put(CoreNBTKeys.TANKS, tanks);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        var loaded = NonNullList.withSize(getTanks(), FluidStack.EMPTY);
        var overflow = new ListTag();
        var seen = new java.util.HashSet<Integer>();
        var tanks = savedEntries(tag);
        for (int i = 0; i < tanks.size(); i++) {
            var entry = tanks.getCompound(i);
            int tank = entry.getInt(TANK_INDEX);
            if (!seen.add(tank)) {
                throw new IllegalArgumentException("Duplicate machine tank index: " + tank);
            }
            if (tank < 0 || tank >= getTanks()) {
                overflow.add(entry.copy());
            } else {
                loaded.set(tank, readEntry(entry));
            }
        }
        boolean[] changed = new boolean[getTanks()];
        for (int tank = 0; tank < getTanks(); tank++) {
            changed[tank] = !StackDataCodecs.fluidsEqual(stacks.get(tank), loaded.get(tank));
            stacks.set(tank, loaded.get(tank));
        }
        overflowTanks = overflow;
        for (int tank = 0; tank < getTanks(); tank++) {
            if (changed[tank]) {
                onContentsChanged(tank);
                changeListener.accept(tank);
            }
        }
    }

    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        return serializeNBT();
    }

    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        deserializeNBT(tag);
    }

    /** Forge 6.x used the singular key, including under an item's BlockEntityTag. */
    public static CompoundTag savedStorage(CompoundTag blockData) {
        String key = blockData.contains(CoreNBTKeys.FLUIDS) ? CoreNBTKeys.FLUIDS : CoreNBTKeys.FLUID;
        return blockData.getCompound(key);
    }

    public static CompoundTag savedStorageWithTankList(CompoundTag storage) {
        var result = storage.copy();
        result.put(CoreNBTKeys.TANKS, savedEntries(storage).copy());
        return result;
    }

    public static FluidStack readSavedTank(CompoundTag storage, int index) {
        return StackDataCodecs.loadFluid(savedTankData(storage, index));
    }

    public static CompoundTag savedTankData(CompoundTag storage, int index) {
        for (var saved : savedEntries(storage)) {
            var entry = (CompoundTag) saved;
            if (entry.getInt(TANK_INDEX) == index) return getStackData(entry);
        }
        return new CompoundTag();
    }

    private static ListTag savedEntries(CompoundTag tag) {
        if (!tag.contains(CoreNBTKeys.TANKS) && tag.contains("FluidName", Tag.TAG_STRING)) {
            // The older single-tank layout saved the Forge stack directly at the root.
            var entries = new ListTag();
            var entry = new CompoundTag();
            entry.putInt(TANK_INDEX, 0);
            entry.put(TANK_CONTENTS, tag.copy());
            entries.add(entry);
            return entries;
        }
        return tag.getList(CoreNBTKeys.TANKS, Tag.TAG_COMPOUND);
    }

    private static FluidStack readEntry(CompoundTag entry) {
        return StackDataCodecs.loadFluid(getStackData(entry));
    }

    private static CompoundTag getStackData(CompoundTag entry) {
        // Forge 6.x saved the stack next to Index and Capacity in the tank list.
        boolean flatStack = !entry.contains(TANK_CONTENTS) && entry.contains("FluidName", Tag.TAG_STRING);
        return flatStack ? entry : entry.getCompound(TANK_CONTENTS);
    }

    // Sided capability access
    private record Sided(MachineFluidHandler master, Direction direction) implements IFluidHandler {
        @Override
        public int getTanks() {
            return master.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return master.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return master.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return master.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (master.getConfig().getIOMode(direction).canInput()) {
                return master.fill(resource, action);
            }

            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (master.getConfig().getIOMode(direction).canOutput()) {
                return master.drain(resource, action);
            }

            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (master.getConfig().getIOMode(direction).canOutput()) {
                return master.drain(maxDrain, action);
            }

            return FluidStack.EMPTY;
        }
    }
}
