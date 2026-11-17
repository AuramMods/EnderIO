package com.enderio.core.common.serialization;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import java.util.Optional;
import java.util.stream.Stream;

/** Missing is optional; malformed present data must remain an error on DFU 6. */
public final class StrictOptionalFieldCodec<A> extends MapCodec<Optional<A>> {
    private final Codec<A> valueCodec;
    private final String name;

    public StrictOptionalFieldCodec(Codec<A> valueCodec, String name) {
        this.valueCodec = valueCodec;
        this.name = name;
    }

    @Override
    public <T> DataResult<Optional<A>> decode(DynamicOps<T> ops, MapLike<T> input) {
        T value = input.get(name);
        if (value == null) return DataResult.success(Optional.empty());
        var parsed = valueCodec.parse(ops, value);
        // Preserve the source codec's explicit partial-recovery behavior without hiding the error.
        return parsed.map(Optional::of).setPartial(parsed.resultOrPartial(message -> {}));
    }

    @Override
    public <T> RecordBuilder<T> encode(Optional<A> value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
        return value.isPresent() ? prefix.add(name, valueCodec.encodeStart(ops, value.get())) : prefix;
    }

    @Override
    public <T> Stream<T> keys(DynamicOps<T> ops) {
        return Stream.of(ops.createString(name));
    }
}
