package com.enderio.core.common.registries;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import java.util.function.Supplier;

public class ItemDeferredHolder<T extends Item> implements Supplier<T>, ItemLike {
    private final RegistryObject<T> delegate;

    ItemDeferredHolder(RegistryObject<T> delegate) {
        this.delegate = delegate;
    }

    public static <T extends Item> ItemDeferredHolder<T> createItem(ResourceLocation key) {
        return createItem(ResourceKey.create(Registries.ITEM, key));
    }

    public static <T extends Item> ItemDeferredHolder<T> createItem(ResourceKey<Item> key) {
        return new ItemDeferredHolder<T>(key);
    }

    protected ItemDeferredHolder(ResourceKey<Item> key) {
        delegate = RegistryObject.create(key.location(), Registries.ITEM, key.location().getNamespace());
    }
    @Override
    public T get() {
        return delegate.get();
    }

    @Override
    public Item asItem() {
        return get();
    }

    public ResourceLocation getId() {
        return delegate.getId();
    }

    public ResourceKey<Item> getKey() {
        return ResourceKey.create(Registries.ITEM, getId());
    }

    public ItemStack toStack() {
        return new ItemStack(get());
    }

    public ItemStack toStack(int count) {
        return new ItemStack(get(), count);
    }
}
