package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.EnderIO;
import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import net.minecraft.core.Holder;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record ClientboundConduitListPacket(int containerId, List<Holder<Conduit<?, ?>>> conduits)
        implements CustomPacketPayload {

    public static final Type<ClientboundConduitListPacket> TYPE = new Type<>(EnderIO.rl("conduit_list"));

    public static final PacketCodec<ClientboundConduitListPacket> STREAM_CODEC = PacketCodec.composite(
            PacketCodecs.INT, ClientboundConduitListPacket::containerId,
            Conduit.STREAM_CODEC.list(ConduitBundleBlockEntity.MAX_CONDUITS),
            ClientboundConduitListPacket::conduits, ClientboundConduitListPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
