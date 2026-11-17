package com.enderio.core.common.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * Tracks a neighboring Forge capability and its invalidation listener.
 * Call refresh before using derived caches: Forge has no notification for an absent
 * capability becoming present, so misses must be queried again as the owner ticks.
 */
public final class NeighborCapability<T> implements AutoCloseable {
    private final Capability<T> capability;
    private final Level level;
    private final BlockPos pos;
    @Nullable private final Direction side;
    private final BooleanSupplier ownerValid;
    private final Runnable changed;
    private final NonNullConsumer<LazyOptional<T>> listener = this::invalidated;
    private LazyOptional<T> observed = LazyOptional.empty();
    @Nullable private T value;
    private boolean initialized;
    private boolean closed;

    private NeighborCapability(Capability<T> capability, Level level, BlockPos pos, @Nullable Direction side,
            BooleanSupplier ownerValid, Runnable changed) {
        this.capability = capability;
        this.level = level;
        this.pos = pos.immutable();
        this.side = side;
        this.ownerValid = ownerValid;
        this.changed = changed;
    }

    public static <T> NeighborCapability<T> create(Capability<T> capability, Level level, BlockPos pos,
            @Nullable Direction side, BooleanSupplier ownerValid, Runnable changed) {
        return new NeighborCapability<>(capability, level, pos, side, ownerValid, changed);
    }

    @Nullable
    public T getCapability() {
        refresh();
        return value;
    }

    public void refresh() {
        if (closed) return;
        if (!ownerValid.getAsBoolean()) {
            close();
            return;
        }

        var next = CapabilityUtil.find(level, pos, capability, side);
        T nextValue = next.orElse(null);
        boolean notify = initialized && value != nextValue;
        if (observed != next) {
            observed.removeListener(listener);
            observed = next;
            // Empty optionals invalidate listeners immediately and must not be watched.
            if (nextValue != null) next.addListener(listener);
        }
        value = nextValue;
        initialized = true;
        if (notify) changed.run();
    }

    private void invalidated(LazyOptional<T> invalidated) {
        if (closed || observed != invalidated) return;
        observed = LazyOptional.empty();
        value = null;
        // Clear first, so callbacks cannot observe the invalidated handler.
        if (ownerValid.getAsBoolean()) changed.run();
        else close();
    }

    @Override
    public void close() {
        closed = true;
        observed.removeListener(listener);
        observed = LazyOptional.empty();
        value = null;
    }
}
