package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.enderio.api.conduits.connection.config.ConnectionConfig;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;

public record SetConduitConnectionConfigPacket(int containerId, ConnectionConfig connectionConfig)
        {

    public static final ResourceLocation ID = EnderIOAPI.rl("client_set_conduit_connection_config");

    public static final PacketCodec<SetConduitConnectionConfigPacket> STREAM_CODEC = PacketCodec
            .composite(PacketCodecs.INT, SetConduitConnectionConfigPacket::containerId, ConnectionConfig.STREAM_CODEC,
                    SetConduitConnectionConfigPacket::connectionConfig, SetConduitConnectionConfigPacket::new);

}
