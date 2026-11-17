package com.enderio.core.common.network.codec;

import com.mojang.datafixers.util.Function4;
import com.mojang.datafixers.util.Function5;
import com.mojang.datafixers.util.Function6;
import com.mojang.datafixers.util.Function7;
import com.mojang.datafixers.util.Function8;
import com.mojang.datafixers.util.Function9;
import com.mojang.datafixers.util.Function3;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;

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

    static <T> PacketCodec<T> unit(T value) {
        return of((buffer, supplied) -> {
            if (!Objects.equals(value, supplied)) throw new EncoderException("Unexpected unit packet value");
        }, buffer -> value);
    }

    static <T> PacketCodec<T> ofMember(BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder) {
        return of((buffer, value) -> encoder.accept(value, buffer), decoder);
    }

    default PacketCodec<Optional<T>> optional() {
        return of((buffer, value) -> {
            buffer.writeBoolean(value.isPresent());
            value.ifPresent(element -> encode(buffer, element));
        }, buffer -> buffer.readBoolean() ? Optional.of(decode(buffer)) : Optional.empty());
    }

    /** Selects a concrete payload codec after decoding its registered discriminator. */
    default <U> PacketCodec<U> dispatch(Function<U, T> discriminator, Function<T, ? extends PacketCodec<? extends U>> codecs) {
        return of((buffer, value) -> {
            T type = discriminator.apply(value);
            encode(buffer, type);
            encodeDispatched(codecs.apply(type), buffer, value);
        }, buffer -> codecs.apply(decode(buffer)).decode(buffer));
    }

    @SuppressWarnings("unchecked")
    private static <U> void encodeDispatched(PacketCodec<? extends U> codec, FriendlyByteBuf buffer, U value) {
        // The discriminator must return the codec for this concrete value type.
        ((PacketCodec<U>) codec).encode(buffer, value);
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
        return map(size -> new LinkedHashMap<>(), keys, values);
    }

    static <K, V, M extends Map<K, V>> PacketCodec<M> map(IntFunction<M> factory, PacketCodec<K> keys, PacketCodec<V> values) {
        return of((buffer, map) -> {
            writeSize(buffer, map.size(), MAX_COLLECTION_SIZE);
            map.forEach((key, value) -> {
                keys.encode(buffer, key);
                values.encode(buffer, value);
            });
        }, buffer -> {
            int size = readSize(buffer, MAX_COLLECTION_SIZE);
            M map = factory.apply(Math.min(size, 256));
            for (int i = 0; i < size; i++) {
                K key = keys.decode(buffer);
                V value = values.decode(buffer);
                if (map.containsKey(key)) throw new DecoderException("Duplicate packet map key");
                map.put(key, value);
            }
            return map;
        });
    }

    static <T, A> PacketCodec<T> composite(PacketCodec<A> a, Function<T, A> getA, Function<A, T> constructor) {
        return a.map(constructor, getA);
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

    static <T, A, B, C, D> PacketCodec<T> composite(PacketCodec<A> a, Function<T, A> getA,
            PacketCodec<B> b, Function<T, B> getB,
            PacketCodec<C> c, Function<T, C> getC,
            PacketCodec<D> d, Function<T, D> getD,
            Function4<A, B, C, D, T> constructor) {
        return of((buffer, value) -> {
            a.encode(buffer, getA.apply(value));
            b.encode(buffer, getB.apply(value));
            c.encode(buffer, getC.apply(value));
            d.encode(buffer, getD.apply(value));
        }, buffer -> constructor.apply(a.decode(buffer), b.decode(buffer), c.decode(buffer), d.decode(buffer)));
    }

    static <T, A, B, C, D, E> PacketCodec<T> composite(PacketCodec<A> a, Function<T, A> getA,
            PacketCodec<B> b, Function<T, B> getB,
            PacketCodec<C> c, Function<T, C> getC,
            PacketCodec<D> d, Function<T, D> getD,
            PacketCodec<E> e, Function<T, E> getE,
            Function5<A, B, C, D, E, T> constructor) {
        return of((buffer, value) -> {
            a.encode(buffer, getA.apply(value));
            b.encode(buffer, getB.apply(value));
            c.encode(buffer, getC.apply(value));
            d.encode(buffer, getD.apply(value));
            e.encode(buffer, getE.apply(value));
        }, buffer -> constructor.apply(a.decode(buffer), b.decode(buffer), c.decode(buffer), d.decode(buffer), e.decode(buffer)));
    }

    static <T, A, B, C, D, E, F> PacketCodec<T> composite(PacketCodec<A> a, Function<T, A> getA,
            PacketCodec<B> b, Function<T, B> getB,
            PacketCodec<C> c, Function<T, C> getC,
            PacketCodec<D> d, Function<T, D> getD,
            PacketCodec<E> e, Function<T, E> getE,
            PacketCodec<F> f, Function<T, F> getF,
            Function6<A, B, C, D, E, F, T> constructor) {
        return of((buffer, value) -> {
            a.encode(buffer, getA.apply(value));
            b.encode(buffer, getB.apply(value));
            c.encode(buffer, getC.apply(value));
            d.encode(buffer, getD.apply(value));
            e.encode(buffer, getE.apply(value));
            f.encode(buffer, getF.apply(value));
        }, buffer -> constructor.apply(a.decode(buffer), b.decode(buffer), c.decode(buffer), d.decode(buffer), e.decode(buffer), f.decode(buffer)));
    }

    static <T, A, B, C, D, E, F, G> PacketCodec<T> composite(PacketCodec<A> a, Function<T, A> getA,
            PacketCodec<B> b, Function<T, B> getB,
            PacketCodec<C> c, Function<T, C> getC,
            PacketCodec<D> d, Function<T, D> getD,
            PacketCodec<E> e, Function<T, E> getE,
            PacketCodec<F> f, Function<T, F> getF,
            PacketCodec<G> g, Function<T, G> getG,
            Function7<A, B, C, D, E, F, G, T> constructor) {
        return of((buffer, value) -> {
            a.encode(buffer, getA.apply(value));
            b.encode(buffer, getB.apply(value));
            c.encode(buffer, getC.apply(value));
            d.encode(buffer, getD.apply(value));
            e.encode(buffer, getE.apply(value));
            f.encode(buffer, getF.apply(value));
            g.encode(buffer, getG.apply(value));
        }, buffer -> constructor.apply(a.decode(buffer), b.decode(buffer), c.decode(buffer), d.decode(buffer), e.decode(buffer), f.decode(buffer), g.decode(buffer)));
    }

    static <T, A, B, C, D, E, F, G, H> PacketCodec<T> composite(PacketCodec<A> a, Function<T, A> getA,
            PacketCodec<B> b, Function<T, B> getB,
            PacketCodec<C> c, Function<T, C> getC,
            PacketCodec<D> d, Function<T, D> getD,
            PacketCodec<E> e, Function<T, E> getE,
            PacketCodec<F> f, Function<T, F> getF,
            PacketCodec<G> g, Function<T, G> getG,
            PacketCodec<H> h, Function<T, H> getH,
            Function8<A, B, C, D, E, F, G, H, T> constructor) {
        return of((buffer, value) -> {
            a.encode(buffer, getA.apply(value));
            b.encode(buffer, getB.apply(value));
            c.encode(buffer, getC.apply(value));
            d.encode(buffer, getD.apply(value));
            e.encode(buffer, getE.apply(value));
            f.encode(buffer, getF.apply(value));
            g.encode(buffer, getG.apply(value));
            h.encode(buffer, getH.apply(value));
        }, buffer -> constructor.apply(a.decode(buffer), b.decode(buffer), c.decode(buffer), d.decode(buffer), e.decode(buffer), f.decode(buffer), g.decode(buffer), h.decode(buffer)));
    }

    static <T, A, B, C, D, E, F, G, H, I> PacketCodec<T> composite(PacketCodec<A> a, Function<T, A> getA,
            PacketCodec<B> b, Function<T, B> getB,
            PacketCodec<C> c, Function<T, C> getC,
            PacketCodec<D> d, Function<T, D> getD,
            PacketCodec<E> e, Function<T, E> getE,
            PacketCodec<F> f, Function<T, F> getF,
            PacketCodec<G> g, Function<T, G> getG,
            PacketCodec<H> h, Function<T, H> getH,
            PacketCodec<I> i, Function<T, I> getI,
            Function9<A, B, C, D, E, F, G, H, I, T> constructor) {
        return of((buffer, value) -> {
            a.encode(buffer, getA.apply(value));
            b.encode(buffer, getB.apply(value));
            c.encode(buffer, getC.apply(value));
            d.encode(buffer, getD.apply(value));
            e.encode(buffer, getE.apply(value));
            f.encode(buffer, getF.apply(value));
            g.encode(buffer, getG.apply(value));
            h.encode(buffer, getH.apply(value));
            i.encode(buffer, getI.apply(value));
        }, buffer -> constructor.apply(a.decode(buffer), b.decode(buffer), c.decode(buffer), d.decode(buffer), e.decode(buffer), f.decode(buffer), g.decode(buffer), h.decode(buffer), i.decode(buffer)));
    }
}
