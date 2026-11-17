package com.enderio.core.common.registries;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** A registered block entity type and its Forge capability factories. */
public final class BlockEntityTypeDeferredHolder<B extends BlockEntity> implements Supplier<BlockEntityType<B>> {
    private final RegistryObject<BlockEntityType<B>> type;
    private final List<AttachedCapability<B, ?>> capabilities;

    public BlockEntityTypeDeferredHolder(RegistryObject<BlockEntityType<B>> type, List<AttachedCapability<B, ?>> capabilities) {
        this.type = type;
        this.capabilities = List.copyOf(capabilities);
    }

    @Override
    public BlockEntityType<B> get() {
        return type.get();
    }

    public BoundCapabilities<B> bind(BlockEntity entity) {
        if (entity.getType() != get()) {
            throw new IllegalArgumentException("Capability factories belong to a different block entity type");
        }
        @SuppressWarnings("unchecked")
        B typedEntity = (B) entity;
        return new BoundCapabilities<>(typedEntity, capabilities);
    }

    public record AttachedCapability<B extends BlockEntity, C>(
        Capability<C> capability, BiFunction<? super B, @Nullable Direction, @Nullable C> factory) {
    }

    /** Lazily constructs one provider per capability/side and participates in Forge invalidation. */
    public static final class BoundCapabilities<B extends BlockEntity> implements ICapabilityProvider {
        private final B entity;
        private final List<AttachedCapability<B, ?>> factories;
        private final Map<Capability<?>, Map<Direction, LazyOptional<?>>> instances = new HashMap<>();

        public BoundCapabilities(B entity, List<AttachedCapability<B, ?>> factories) {
            this.entity = entity;
            this.factories = List.copyOf(factories);
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
            var sides = instances.get(capability);
            if (sides != null && sides.containsKey(side)) {
                return sides.get(side).cast();
            }
            for (var binding : factories) {
                if (binding.capability() != capability) continue;
                Object value = binding.factory().apply(entity, side);
                if (value != null) {
                    LazyOptional<?> instance = LazyOptional.of(() -> value);
                    instances.computeIfAbsent(capability, ignored -> new HashMap<>()).put(side, instance);
                    return instance.cast();
                }
            }
            return LazyOptional.empty();
        }

        public void invalidate() {
            // Remove before notifying listeners, so an observer cannot retrieve
            // an optional which is being invalidated from this cache.
            var previous = List.copyOf(instances.values());
            instances.clear();
            previous.forEach(sides -> sides.values().forEach(LazyOptional::invalidate));
        }
    }
}
