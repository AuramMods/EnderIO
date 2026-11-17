package com.enderio.core.common.registries;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.Capability;
import com.enderio.core.common.capability.BoundCapabilityProvider;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
    public static final class BoundCapabilities<B extends BlockEntity> extends BoundCapabilityProvider<B> {
        public BoundCapabilities(B entity, List<AttachedCapability<B, ?>> factories) {
            super(entity, factories.stream().<Binding<B, ?>>map(BoundCapabilities::binding).toList());
        }

        private static <B extends BlockEntity, C> Binding<B, C> binding(AttachedCapability<B, C> binding) {
            return new Binding<>(binding.capability(), binding.factory());
        }
    }
}
