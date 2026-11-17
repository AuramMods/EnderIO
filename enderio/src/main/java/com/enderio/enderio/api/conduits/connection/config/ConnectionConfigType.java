package com.enderio.enderio.api.conduits.connection.config;

import com.mojang.serialization.MapCodec;
import com.enderio.core.common.network.codec.PacketCodec;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Supplier;

@ApiStatus.Experimental
public record ConnectionConfigType<T extends ConnectionConfig>(MapCodec<T> codec,
                                                               PacketCodec<T> streamCodec, Supplier<T> defaultSupplier) {
    public T getDefault() {
        return defaultSupplier.get();
    }
}
