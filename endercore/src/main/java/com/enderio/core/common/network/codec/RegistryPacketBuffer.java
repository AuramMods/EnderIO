package com.enderio.core.common.network.codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;

/** Native buffer view with the current connection/world's dynamic registries. Does not retain or own the buffer. */
public final class RegistryPacketBuffer extends FriendlyByteBuf {
    private final RegistryAccess registries;

    public RegistryPacketBuffer(ByteBuf source, RegistryAccess registries) {
        super(source);
        this.registries = Objects.requireNonNull(registries);
    }

    public RegistryAccess registryAccess() {
        return registries;
    }
}
