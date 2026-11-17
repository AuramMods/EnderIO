package com.enderio.modded_conduits.common.modules.refinedstorage;

import com.enderio.enderio.api.conduits.connection.config.ConnectionConfig;
import com.enderio.enderio.api.conduits.connection.config.ConnectionConfigType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;

public record RSConduitConnectionConfig(boolean isConnected) implements ConnectionConfig {

    public static final RSConduitConnectionConfig DEFAULT = new RSConduitConnectionConfig(true);

    public static final MapCodec<RSConduitConnectionConfig> CODEC = RecordCodecBuilder.mapCodec(
            inst -> inst.group(Codec.BOOL.fieldOf("isConnected").forGetter(RSConduitConnectionConfig::isConnected))
                    .apply(inst, RSConduitConnectionConfig::new));

    public static final PacketCodec<RSConduitConnectionConfig> STREAM_CODEC = PacketCodecs.BOOL
            .map(RSConduitConnectionConfig::new, RSConduitConnectionConfig::isConnected);

    public static final ConnectionConfigType<RSConduitConnectionConfig> TYPE = new ConnectionConfigType<>(CODEC,
            STREAM_CODEC, () -> DEFAULT);

    @Override
    public ConnectionConfigType<?> type() {
        return TYPE;
    }

    @Override
    public ConnectionConfig reconnected() {
        return new RSConduitConnectionConfig(true);
    }

    @Override
    public ConnectionConfig disconnected() {
        return new RSConduitConnectionConfig(false);
    }
}
