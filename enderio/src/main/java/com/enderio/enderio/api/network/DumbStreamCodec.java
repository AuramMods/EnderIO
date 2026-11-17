package com.enderio.enderio.api.network;

import com.enderio.core.common.network.codec.PacketCodec;

import java.util.function.Supplier;

public class DumbStreamCodec {
    public static <T> PacketCodec<T> of(Supplier<T> factory) {
        return PacketCodec.of(
            (buf, obj) -> {},
            buf -> factory.get());
    }
}
