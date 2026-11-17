package com.enderio.core.common.network.menu;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.menu.payload.SlotPayload;

import java.util.List;

public record ClientboundSyncSlotDataPacket(int containerId, List<PayloadPair> payloads) {

    public static final PacketCodec<ClientboundSyncSlotDataPacket> STREAM_CODEC = PacketCodec
            .composite(PacketCodecs.INT, ClientboundSyncSlotDataPacket::containerId,
                    PayloadPair.STREAM_CODEC.list(), ClientboundSyncSlotDataPacket::payloads,
                    ClientboundSyncSlotDataPacket::new);


    public record PayloadPair(short index, SlotPayload payload) {
        public static final PacketCodec<PayloadPair> STREAM_CODEC = PacketCodec.composite(
                PacketCodecs.SHORT, PayloadPair::index, SlotPayload.STREAM_CODEC, PayloadPair::payload,
                PayloadPair::new);
    }
}
