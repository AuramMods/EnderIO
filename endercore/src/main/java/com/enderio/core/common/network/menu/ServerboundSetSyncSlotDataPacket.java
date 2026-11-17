package com.enderio.core.common.network.menu;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.menu.payload.SlotPayload;

public record ServerboundSetSyncSlotDataPacket(int containerId, short index, SlotPayload payload) {

    public static final PacketCodec<ServerboundSetSyncSlotDataPacket> STREAM_CODEC = PacketCodec
            .composite(PacketCodecs.INT, ServerboundSetSyncSlotDataPacket::containerId, PacketCodecs.SHORT,
                    ServerboundSetSyncSlotDataPacket::index, SlotPayload.STREAM_CODEC,
                    ServerboundSetSyncSlotDataPacket::payload, ServerboundSetSyncSlotDataPacket::new);

}
