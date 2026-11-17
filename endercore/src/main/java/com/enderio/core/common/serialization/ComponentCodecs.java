package com.enderio.core.common.serialization;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** Native structured JSON in data files, and native component JSON strings in NBT. */
public final class ComponentCodecs {
    private static final Codec<Component> STRUCTURED = JsonCodec.of(
        json -> Objects.requireNonNull(Component.Serializer.fromJson(json), "Component cannot be null"),
        Component.Serializer::toJsonTree);

    private static final Codec<Component> NBT = Codec.STRING.comapFlatMap(json -> {
        try {
            var component = Component.Serializer.fromJson(json);
            return component == null ? DataResult.error(() -> "Component cannot be null") : DataResult.success(component);
        } catch (RuntimeException error) {
            return DataResult.error(() -> "Invalid component JSON: " + error.getMessage());
        }
    }, Component.Serializer::toJson);

    public static final Codec<Component> COMPONENT = new Codec<>() {
        @Override
        public <T> DataResult<Pair<Component, T>> decode(DynamicOps<T> ops, T input) {
            return forOps(ops).decode(ops, input);
        }

        @Override
        public <T> DataResult<T> encode(Component input, DynamicOps<T> ops, T prefix) {
            return forOps(ops).encode(input, ops, prefix);
        }
    };

    private static Codec<Component> forOps(DynamicOps<?> ops) {
        // Test the value type so RegistryOps wrapping NbtOps uses the same format.
        // Generic JSON-to-NBT conversion loses boolean styles and mixed argument lists.
        return ops.empty() instanceof Tag ? NBT : STRUCTURED;
    }

    private ComponentCodecs() {}
}
