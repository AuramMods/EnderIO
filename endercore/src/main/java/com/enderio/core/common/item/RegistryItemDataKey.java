package com.enderio.core.common.item;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Stores only a registry entry's stable ID. Holders are resolved from an explicitly supplied current world. */
public final class RegistryItemDataKey<T> {
    private final ItemDataKey<ResourceLocation> storage;
    private final ResourceKey<? extends Registry<T>> registry;

    public RegistryItemDataKey(ResourceLocation id, ResourceKey<? extends Registry<T>> registry) {
        storage = new ItemDataKey<>(id, ResourceLocation.CODEC);
        this.registry = registry;
    }

    public @Nullable ResourceLocation getId(ItemStack stack) { return storage.get(stack); }

    public boolean has(ItemStack stack) { return storage.has(stack); }

    public @Nullable Holder<T> get(ItemStack stack, RegistryAccess registries) {
        ResourceLocation id = getId(stack);
        if (id == null) return null;
        return registries.registryOrThrow(registry).getHolder(ResourceKey.create(registry, id)).orElse(null);
    }

    public void set(ItemStack stack, Holder<T> holder) {
        ResourceKey<T> entry = holder.unwrapKey().orElseThrow(() -> new IllegalArgumentException("Cannot save a direct registry holder"));
        if (!entry.isFor(registry) || !holder.isBound()) throw new IllegalArgumentException("Holder belongs to a different registry or is unbound");
        storage.set(stack, entry.location());
    }

    public void setId(ItemStack stack, ResourceLocation id) { storage.set(stack, id); }

    public void remove(ItemStack stack) { storage.remove(stack); }
}
