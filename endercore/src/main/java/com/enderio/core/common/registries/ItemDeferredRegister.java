package com.enderio.core.common.registries;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Function;
import java.util.function.Supplier;

public class ItemDeferredRegister extends EnderDeferredRegister<Item> {

    public static ItemDeferredRegister create(String namespace) {
        return new ItemDeferredRegister(namespace);
    }

    protected ItemDeferredRegister(String namespace) {
        super(Registries.ITEM, namespace);
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

    public <T extends Item> RegistryObject<T> registerItem(String name, Function<Item.Properties, ? extends T> factory, Item.Properties properties) {
        return register(name, () -> factory.apply(properties));
    }

    public <T extends Item> RegistryObject<T> registerItem(String name, Function<Item.Properties, ? extends T> factory) {
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

        public RegistryObject<T> build() {
            return register(name, factory);
        }
    }
}
