package com.enderio.enderio.content.conduits.legacy;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import org.apache.commons.lang3.NotImplementedException;

@Deprecated(since = "8.0.0")
public sealed interface ConnectionState permits StaticConnectionStates, DynamicConnectionState {

    Codec<ConnectionState> CODEC = Codec.either(StaticConnectionStates.CODEC, DynamicConnectionState.CODEC)
            .xmap(e -> e.left().isPresent() ? e.left().get() : e.right().get(), e -> {
                if (e instanceof StaticConnectionStates staticConnectionStates) {
                    return Either.left(staticConnectionStates);
                } else if (e instanceof DynamicConnectionState dynamicConnectionState) {
                    return Either.right(dynamicConnectionState);
                }

                throw new NotImplementedException();
            });

    PacketCodec<ConnectionState> STREAM_CODEC = PacketCodecs
            .either(StaticConnectionStates.STREAM_CODEC, DynamicConnectionState.STREAM_CODEC)
            .map(e -> e.left().isPresent() ? e.left().get() : e.right().get(), e -> {
                if (e instanceof StaticConnectionStates staticConnectionStates) {
                    return Either.left(staticConnectionStates);
                } else if (e instanceof DynamicConnectionState dynamicConnectionState) {
                    return Either.right(dynamicConnectionState);
                }

                throw new NotImplementedException();
            });

    boolean isConnection();
}
