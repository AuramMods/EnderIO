package com.enderio.core.common.item;

import com.enderio.core.common.capability.ItemCapabilityRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Typed item state stored in native 1.20.1 stack NBT. Values are encoded on write and
 * decoded on read, so mutable values never alias the stack or another stack's data.
 * Registry-backed codecs require the overloads taking the current world's registry access.
 * Vanilla ItemStack copy/save/network operations carry this NBT without a custom transport.
 */
public final class ItemDataKey<T> {
    private static final String DATA_TAG = "enderio:data";
    private static final String REMOVED_TAG = "enderio:removed_data";

    public static boolean hasStoredData(ItemStack stack) {
        return stack.getTag() != null && (stack.getTag().contains(DATA_TAG) || stack.getTag().contains(REMOVED_TAG));
    }

    /** Copy explicitly stored values, optionally selecting IDs, without sharing mutable NBT. */
    public static void copyStoredData(ItemStack source, ItemStack target, @Nullable java.util.Set<ResourceLocation> include) {
        if (source.getTag() == null || !source.getTag().contains(DATA_TAG)) return;
        var data = source.getTag().getCompound(DATA_TAG);
        for (String key : data.getAllKeys()) {
            if (include == null || include.contains(ResourceLocation.parse(key))) {
                target.getOrCreateTagElement(DATA_TAG).put(key, data.get(key).copy());
                var removed = target.getTagElement(REMOVED_TAG);
                if (removed != null) removed.remove(key);
            }
        }
        ItemCapabilityRegistry.refresh(target);
    }

    private final ResourceLocation id;
    private final Codec<T> codec;
    private final boolean refreshCapabilities;
    private final List<String> aliases;
    private final Map<Item, Supplier<? extends T>> defaults = new IdentityHashMap<>();

    public ItemDataKey(ResourceLocation id, Codec<T> codec) {
        this(id, codec, true);
    }

    /**
     * Set refreshCapabilities to false only for data whose attached views read the stack
     * live (for example energy). Snapshot providers must be refreshed after replacement.
     * Aliases are earlier names inside this port's NBT container, not 1.21 world migration.
     */
    public ItemDataKey(ResourceLocation id, Codec<T> codec, boolean refreshCapabilities, ResourceLocation... aliases) {
        this.id = Objects.requireNonNull(id);
        this.codec = Objects.requireNonNull(codec);
        this.refreshCapabilities = refreshCapabilities;
        this.aliases = java.util.Arrays.stream(aliases).map(ResourceLocation::toString).toList();
    }

    public ResourceLocation id() {
        return id;
    }

    /** Register while constructing the item; the supplier is evaluated when its default is read. */
    public <I extends Item> I withDefault(I item, Supplier<? extends T> value) {
        defaults.put(item, Objects.requireNonNull(value));
        return item;
    }

    @Nullable
    public T get(ItemStack stack) {
        return get(stack, NbtOps.INSTANCE);
    }

    @Nullable
    public T get(ItemStack stack, RegistryAccess registries) {
        return get(stack, RegistryOps.create(NbtOps.INSTANCE, registries));
    }

    public T getOrDefault(ItemStack stack, T fallback) {
        T value = get(stack);
        return value == null ? fallback : value;
    }

    public T getOrDefault(ItemStack stack, RegistryAccess registries, T fallback) {
        T value = get(stack, registries);
        return value == null ? fallback : value;
    }

    public boolean has(ItemStack stack) {
        return get(stack) != null;
    }

    public boolean has(ItemStack stack, RegistryAccess registries) {
        return get(stack, registries) != null;
    }

    public void set(ItemStack stack, T value) {
        set(stack, value, NbtOps.INSTANCE);
    }

    public void set(ItemStack stack, RegistryAccess registries, T value) {
        set(stack, value, RegistryOps.create(NbtOps.INSTANCE, registries));
    }

    /** Removes the value, including an item's default; the removal survives copying and saving. */
    public void remove(ItemStack stack) {
        if (stack.isEmpty()) return;
        CompoundTag root = copyRoot(stack);
        clearValues(root);
        if (defaults.containsKey(stack.getItem())) {
            CompoundTag removed = compound(root, REMOVED_TAG);
            removed.putBoolean(id.toString(), true);
            root.put(REMOVED_TAG, removed);
        }
        commit(stack, root);
    }

    /** Discards an override/removal and makes the item's default visible again. */
    public void reset(ItemStack stack) {
        if (stack.isEmpty()) return;
        CompoundTag root = copyRoot(stack);
        clearValues(root);
        commit(stack, root);
    }

    @Nullable
    private T get(ItemStack stack, DynamicOps<Tag> ops) {
        if (stack.isEmpty()) return null;
        CompoundTag root = stack.getTag();
        if (root != null) {
            CompoundTag removed = compound(root, REMOVED_TAG);
            if (removed.getBoolean(id.toString())) return null;
            CompoundTag data = compound(root, DATA_TAG);
            Tag encoded = data.get(id.toString());
            if (encoded == null) {
                for (String alias : aliases) {
                    if (removed.getBoolean(alias)) return null;
                    encoded = data.get(alias);
                    if (encoded != null) break;
                }
            }
            if (encoded != null) return decode(ops, encoded);
        }
        Tag defaultTag = encodeDefault(stack, ops);
        return defaultTag == null ? null : decode(ops, defaultTag);
    }

    private void set(ItemStack stack, T value, DynamicOps<Tag> ops) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Cannot store " + id + " on an empty stack");
        // Complete all fallible serialization before mutating the stack or invalidating its views.
        Tag encoded = result(codec.encodeStart(ops, Objects.requireNonNull(value)), "encode").copy();
        Tag defaultTag = encodeDefault(stack, ops);
        CompoundTag root = copyRoot(stack);
        clearValues(root);
        if (!encoded.equals(defaultTag)) {
            CompoundTag data = compound(root, DATA_TAG);
            data.put(id.toString(), encoded);
            root.put(DATA_TAG, data);
        }
        commit(stack, root);
    }

    @Nullable
    private Tag encodeDefault(ItemStack stack, DynamicOps<Tag> ops) {
        var factory = defaults.get(stack.getItem());
        return factory == null ? null : result(codec.encodeStart(ops, Objects.requireNonNull(factory.get())), "encode default").copy();
    }

    private T decode(DynamicOps<Tag> ops, Tag encoded) {
        return result(codec.parse(ops, encoded.copy()), "decode");
    }

    private <V> V result(DataResult<V> result, String operation) {
        // Partial decoding must not silently discard state that could not be understood.
        return result.result().orElseThrow(() -> new IllegalArgumentException("Cannot " + operation + " item data " + id
            + ": " + result.error().map(DataResult.PartialResult::message).orElse("unknown codec error")));
    }

    private static CompoundTag copyRoot(ItemStack stack) {
        return stack.getTag() == null ? new CompoundTag() : stack.getTag().copy();
    }

    private static CompoundTag compound(CompoundTag root, String key) {
        Tag value = root.get(key);
        if (value == null) return new CompoundTag();
        if (value instanceof CompoundTag compound) return compound;
        throw new IllegalArgumentException("Invalid item data container " + key + ": expected a compound tag");
    }

    private void clearValues(CompoundTag root) {
        for (String container : List.of(DATA_TAG, REMOVED_TAG)) {
            CompoundTag values = compound(root, container);
            values.remove(id.toString());
            aliases.forEach(values::remove);
            if (values.isEmpty()) root.remove(container);
        }
    }

    private void commit(ItemStack stack, CompoundTag root) {
        CompoundTag replacement = root.isEmpty() ? null : root;
        if (Objects.equals(stack.getTag(), replacement)) return;
        stack.setTag(replacement);
        if (refreshCapabilities) ItemCapabilityRegistry.refresh(stack);
    }
}
