package com.enderio.modded_conduits.common.modules.appeng;

import com.enderio.enderio.api.conduits.connection.config.ConnectionConfig;
import com.enderio.enderio.api.conduits.connection.config.ConnectionConfigType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;

public record MEConduitConnectionConfig(boolean isConnected) implements ConnectionConfig {

    public static final MEConduitConnectionConfig DEFAULT = new MEConduitConnectionConfig(true);

    public static final MapCodec<MEConduitConnectionConfig> CODEC = RecordCodecBuilder.mapCodec(
            inst -> inst.group(Codec.BOOL.fieldOf("isConnected").forGetter(MEConduitConnectionConfig::isConnected))
                    .apply(inst, MEConduitConnectionConfig::new));

    public static final PacketCodec<MEConduitConnectionConfig> STREAM_CODEC = PacketCodecs.BOOL
            .map(MEConduitConnectionConfig::new, MEConduitConnectionConfig::isConnected);

    public static final ConnectionConfigType<MEConduitConnectionConfig> TYPE = new ConnectionConfigType<>(CODEC,
            STREAM_CODEC, () -> DEFAULT);

    @Override
    public ConnectionConfigType<?> type() {
        return TYPE;
    }

    @Override
    public ConnectionConfig reconnected() {
        return new MEConduitConnectionConfig(true);
    }

    @Override
    public ConnectionConfig disconnected() {
        return new MEConduitConnectionConfig(false);
    }
}
