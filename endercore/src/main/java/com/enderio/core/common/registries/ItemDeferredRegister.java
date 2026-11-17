package com.enderio.core.common.registries;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.Collection;

public class ItemDeferredRegister {
    private final EnderDeferredRegister<Item> delegate;

    public static ItemDeferredRegister create(String namespace) {
        return new ItemDeferredRegister(namespace);
    }

    protected ItemDeferredRegister(String namespace) {
        delegate = new EnderDeferredRegister<>(Registries.ITEM, namespace);
    }

    public <T extends Item> ItemDeferredHolder<T> register(String name, Supplier<? extends T> factory) {
        return new ItemDeferredHolder<>(delegate.register(name, factory));
    }

    public <T extends Item> ItemDeferredHolder<T> register(String name, Function<ResourceLocation, ? extends T> factory) {
        return new ItemDeferredHolder<>(delegate.register(name, factory));
    }

    public void register(IEventBus bus) {
        delegate.register(bus);
    }

    public void addAlias(ResourceLocation oldId, ResourceLocation newId) {
        delegate.addAlias(oldId, newId);
    }

    public Collection<RegistryObject<Item>> getEntries() {
        return delegate.getEntries();
    }

    public ItemDeferredHolder<BlockItem> registerSimpleBlockItem(BlockDeferredHolder<?> block) {
        return register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public <T extends Item> Builder<T> builder(String name, Supplier<? extends T> factory) {
        return new Builder<>(name, factory);
    }

    public <T extends Item> Builder<T> builder(String name, Function<Item.Properties, ? extends T> factory) {
        return new Builder<>(name, () -> factory.apply(new Item.Properties()));
    }

    public <T extends Item> Builder<T> builder(String name, Function<Item.Properties, ? extends T> factory, Item.Properties properties) {
        return new Builder<>(name, () -> factory.apply(properties));
    }

    public <T extends Item> ItemDeferredHolder<T> registerItem(String name, Function<Item.Properties, ? extends T> factory, Item.Properties properties) {
        return register(name, () -> factory.apply(properties));
    }

    public <T extends Item> ItemDeferredHolder<T> registerItem(String name, Function<Item.Properties, ? extends T> factory) {
        return registerItem(name, factory, new Item.Properties());
    }

    public class Builder<T extends Item> {
        private final String name;
        private final Supplier<? extends T> factory;

        public Builder(String name, Supplier<? extends T> factory) {
            this.name = name;
            this.factory = factory;
        }

        // TODO: Capabilties

        public ItemDeferredHolder<T> build() {
            return register(name, factory);
        }
    }
}
