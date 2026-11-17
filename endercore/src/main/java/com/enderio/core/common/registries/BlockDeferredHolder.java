package com.enderio.core.common.registries;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/** A Forge registration handle usable wherever a block's item form is accepted. */
public final class BlockDeferredHolder<T extends Block> implements Supplier<T>, ItemLike {
    private final RegistryObject<T> delegate;

    BlockDeferredHolder(RegistryObject<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T get() {
        return delegate.get();
    }

    public ResourceLocation getId() {
        return delegate.getId();
    }

    public ResourceKey<Block> getKey() {
        return ResourceKey.create(Registries.BLOCK, getId());
    }

    @Override
    public Item asItem() {
        return get().asItem();
    }

    public ItemStack toStack() {
        return toStack(1);
    }

    public ItemStack toStack(int count) {
        return new ItemStack(this, count);
    }
}
