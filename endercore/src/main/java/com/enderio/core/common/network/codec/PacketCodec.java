package com.enderio.core.common.network.codec;

import com.mojang.datafixers.util.Function3;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Ender IO packet serialization using Forge 1.20.1's native buffer. */
public interface PacketCodec<T> {
    int MAX_COLLECTION_SIZE = 65_536;

    void encode(FriendlyByteBuf buffer, T value);

    T decode(FriendlyByteBuf buffer);

    static <T> PacketCodec<T> of(BiConsumer<FriendlyByteBuf, T> encoder, Function<FriendlyByteBuf, T> decoder) {
        return new PacketCodec<>() {
            @Override
            public void encode(FriendlyByteBuf buffer, T value) {
                encoder.accept(buffer, value);
            }

            @Override
            public T decode(FriendlyByteBuf buffer) {
                return decoder.apply(buffer);
            }
        };
    }

    default <U> PacketCodec<U> map(Function<T, U> from, Function<U, T> to) {
        return of((buffer, value) -> encode(buffer, to.apply(value)), buffer -> from.apply(decode(buffer)));
    }

    default PacketCodec<List<T>> list() {
        return list(MAX_COLLECTION_SIZE);
    }

    default PacketCodec<List<T>> list(int maximum) {
        return of((buffer, values) -> {
            writeSize(buffer, values.size(), maximum);
            values.forEach(value -> encode(buffer, value));
        }, buffer -> {
            int size = readSize(buffer, maximum);
            List<T> values = new ArrayList<>(Math.min(size, 256));
            for (int i = 0; i < size; i++) values.add(decode(buffer));
            return values;
        });
    }

    static <K, V> PacketCodec<Map<K, V>> map(PacketCodec<K> keys, PacketCodec<V> values) {
        return of((buffer, map) -> {
            writeSize(buffer, map.size(), MAX_COLLECTION_SIZE);
            map.forEach((key, value) -> {
                keys.encode(buffer, key);
                values.encode(buffer, value);
            });
        }, buffer -> {
            int size = readSize(buffer, MAX_COLLECTION_SIZE);
            Map<K, V> map = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) {
                K key = keys.decode(buffer);
                V value = values.decode(buffer);
                if (map.putIfAbsent(key, value) != null) throw new DecoderException("Duplicate packet map key");
            }
            return map;
        });
    }

    static int readSize(FriendlyByteBuf buffer, int maximum) {
        int size = buffer.readVarInt();
        if (size < 0 || size > maximum) throw new DecoderException("Invalid collection size: " + size);
        return size;
    }

    static void writeSize(FriendlyByteBuf buffer, int size, int maximum) {
        if (size < 0 || size > maximum) throw new EncoderException("Invalid collection size: " + size);
        buffer.writeVarInt(size);
    }

    static <T, A, B> PacketCodec<T> composite(PacketCodec<A> a, Function<T, A> getA,
            PacketCodec<B> b, Function<T, B> getB, BiFunction<A, B, T> constructor) {
        return of((buffer, value) -> {
            a.encode(buffer, getA.apply(value));
            b.encode(buffer, getB.apply(value));
        }, buffer -> constructor.apply(a.decode(buffer), b.decode(buffer)));
    }

    static <T, A, B, C> PacketCodec<T> composite(PacketCodec<A> a, Function<T, A> getA,
            PacketCodec<B> b, Function<T, B> getB, PacketCodec<C> c, Function<T, C> getC,
            Function3<A, B, C, T> constructor) {
        return of((buffer, value) -> {
            a.encode(buffer, getA.apply(value));
            b.encode(buffer, getB.apply(value));
            c.encode(buffer, getC.apply(value));
        }, buffer -> constructor.apply(a.decode(buffer), b.decode(buffer), c.decode(buffer)));
    }
}
