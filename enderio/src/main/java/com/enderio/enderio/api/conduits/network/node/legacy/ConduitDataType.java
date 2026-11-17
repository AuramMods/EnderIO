package com.enderio.enderio.api.conduits.network.node.legacy;

import com.mojang.serialization.MapCodec;
import com.enderio.core.common.network.codec.PacketCodec;

import java.util.function.Supplier;

@Deprecated(since = "8.0.0")
public record ConduitDataType<T extends ConduitData<T>>(MapCodec<T> codec,
        PacketCodec<T> streamCodec, Supplier<T> factory) {
}
