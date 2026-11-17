package com.enderio.core.common.registries;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;

/** Block factories evaluated by Forge's deferred registration lifecycle. */
public final class BlockDeferredRegister {
    private final EnderDeferredRegister<Block> delegate;

    private BlockDeferredRegister(String namespace) {
        delegate = new EnderDeferredRegister<>(Registries.BLOCK, namespace);
    }

    public static BlockDeferredRegister create(String namespace) {
        return new BlockDeferredRegister(namespace);
    }

    public <T extends Block> BlockDeferredHolder<T> register(String name, Supplier<? extends T> factory) {
        return new BlockDeferredHolder<>(delegate.register(name, factory));
    }

    public <T extends Block> BlockDeferredHolder<T> registerBlock(String name,
            Function<BlockBehaviour.Properties, ? extends T> factory, BlockBehaviour.Properties properties) {
        return register(name, () -> factory.apply(properties));
    }

    public <T extends Block> BlockDeferredHolder<T> registerBlock(String name,
            Function<BlockBehaviour.Properties, ? extends T> factory) {
        return registerBlock(name, factory, BlockBehaviour.Properties.of());
    }

    public void addAlias(ResourceLocation oldId, ResourceLocation newId) {
        delegate.addAlias(oldId, newId);
    }

    public void register(IEventBus bus) {
        delegate.register(bus);
    }

    public Collection<RegistryObject<Block>> getEntries() {
        return delegate.getEntries();
    }
}
