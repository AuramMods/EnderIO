package com.enderio.core.common.capability;

import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;
import java.util.function.BiFunction;

/** Owns the lazily created Forge optionals for one object, with a separate value per side. */
public class BoundCapabilityProvider<O> implements ICapabilityProvider {
    private static final Map<Object, List<WeakReference<BoundCapabilityProvider<?>>>> LIVE_PROVIDERS = new WeakHashMap<>();
    public record Binding<O, C>(Capability<C> capability,
            BiFunction<? super O, @Nullable Direction, @Nullable C> factory) {}

    private final O owner;
    private final List<Binding<O, ?>> factories;
    private final Map<Capability<?>, Map<Direction, LazyOptional<?>>> instances = new HashMap<>();

    public BoundCapabilityProvider(O owner, List<Binding<O, ?>> factories) {
        this.owner = owner;
        this.factories = List.copyOf(factories);
        synchronized (LIVE_PROVIDERS) {
            var references = LIVE_PROVIDERS.computeIfAbsent(owner, ignored -> new ArrayList<>());
            references.removeIf(reference -> reference.get() == null);
            references.add(new WeakReference<>(this));
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        var sides = instances.get(capability);
        if (sides != null && sides.containsKey(side)) return sides.get(side).cast();
        for (var binding : factories) {
            if (binding.capability() != capability) continue;
            Object value = binding.factory().apply(owner, side);
            if (value != null) {
                LazyOptional<?> instance = LazyOptional.of(() -> value);
                instances.computeIfAbsent(capability, ignored -> new HashMap<>()).put(side, instance);
                return instance.cast();
            }
        }
        // A missing capability may become available later, so never retain misses.
        return LazyOptional.empty();
    }

    public void invalidate() {
        invalidate(takeInstances());
    }

    private List<LazyOptional<?>> takeInstances() {
        var previous = instances.values().stream().flatMap(sides -> sides.values().stream()).toList();
        instances.clear();
        return previous;
    }

    private static void invalidate(List<LazyOptional<?>> instances) {
        instances.forEach(LazyOptional::invalidate);
    }

    /** Clears EnderCore-owned views after data changes, preserving other mods' provider lifetimes. */
    public static void refresh(Object owner) {
        List<BoundCapabilityProvider<?>> providers = new ArrayList<>();
        synchronized (LIVE_PROVIDERS) {
            var references = LIVE_PROVIDERS.get(owner);
            if (references != null) {
                for (var reference : references) {
                    var provider = reference.get();
                    if (provider != null) providers.add(provider);
                }
                references.removeIf(reference -> reference.get() == null);
            }
        }
        // Clear every owned provider before any callbacks can query the changed data.
        var previous = providers.stream().flatMap(provider -> provider.takeInstances().stream()).toList();
        invalidate(previous);
    }
}
