package com.enderio.core.common.registries;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class BlockEntityTypeDeferredRegister extends EnderDeferredRegister<BlockEntityType<?>> {
    private final Map<ResourceLocation, BlockEntityTypeDeferredHolder<?>> capabilityHolders = new HashMap<>();

    public static BlockEntityTypeDeferredRegister create(String namespace) {
        return new BlockEntityTypeDeferredRegister(namespace);
    }

    protected BlockEntityTypeDeferredRegister(String namespace) {
        super(Registries.BLOCK_ENTITY_TYPE, namespace);
    }

    @SafeVarargs
    public final <T extends BlockEntity> RegistryObject<BlockEntityType<T>> register(String name,
        BlockEntityType.BlockEntitySupplier<? extends T> factory, Supplier<Block>... validBlocks) {
        return register(name, () -> BlockEntityType.Builder.<T>of(factory,
            Arrays.stream(validBlocks).map(Supplier::get).toArray(Block[]::new)).build(null));
    }

    public <T extends BlockEntity> Builder<T> builder(String name, BlockEntityType.BlockEntitySupplier<? extends T> factory) {
        return new Builder<>(name, factory);
    }

    @SafeVarargs
    public final <T extends BlockEntity> Builder<T> builder(String name, BlockEntityType.BlockEntitySupplier<? extends T> factory,
        Supplier<Block>... validBlocks) {
        return new Builder<>(name, factory, Arrays.asList(validBlocks));
    }

    @Override
    public void register(IEventBus bus) {
        super.register(bus);
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, this::attachCapabilities);
    }

    private void attachCapabilities(AttachCapabilitiesEvent<BlockEntity> event) {
        var holder = capabilityHolders.get(ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(event.getObject().getType()));
        if (holder != null) {
            var provider = holder.bind(event.getObject());
            event.addCapability(new ResourceLocation(getNamespace(), "block_capabilities"), provider);
            event.addListener(provider::invalidate);
        }
    }

    public class Builder<T extends BlockEntity> {
        private final String name;
        private final BlockEntityType.BlockEntitySupplier<? extends T> factory;
        private final List<Supplier<Block>> validBlocks = new ArrayList<>();
        private final List<BlockEntityTypeDeferredHolder.AttachedCapability<T, ?>> attachedCapabilities = new ArrayList<>();

        private Builder(String name, BlockEntityType.BlockEntitySupplier<? extends T> factory) {
            this.name = name;
            this.factory = factory;
        }

        private Builder(String name, BlockEntityType.BlockEntitySupplier<? extends T> factory, List<Supplier<Block>> validBlocks) {
            this(name, factory);
            this.validBlocks.addAll(validBlocks);
        }

        public Builder<T> validBlock(Supplier<Block> validBlock) {
            validBlocks.add(validBlock);
            return this;
        }

        public <C> Builder<T> capability(Capability<C> capability, BiFunction<? super T, @Nullable Direction, @Nullable C> provider) {
            attachedCapabilities.add(new BlockEntityTypeDeferredHolder.AttachedCapability<>(capability, provider));
            return this;
        }

        public Builder<T> apply(Consumer<Builder<T>> applicator) {
            applicator.accept(this);
            return this;
        }

        public RegistryObject<BlockEntityType<T>> build() {
            var holder = register(name, () -> BlockEntityType.Builder.<T>of(factory,
                validBlocks.stream().map(Supplier::get).toArray(Block[]::new)).build(null));
            if (!attachedCapabilities.isEmpty()) {
                capabilityHolders.put(holder.getId(), new BlockEntityTypeDeferredHolder<>(holder, attachedCapabilities));
            }
            return holder;
        }
    }
}
