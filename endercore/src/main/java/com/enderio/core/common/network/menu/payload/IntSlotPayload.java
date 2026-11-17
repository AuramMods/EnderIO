package com.enderio.core.common.network.menu.payload;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;

public record IntSlotPayload(int value) implements SlotPayload {

    public static final PacketCodec<IntSlotPayload> STREAM_CODEC = PacketCodecs.INT
            .map(IntSlotPayload::new, IntSlotPayload::value)
            ;

    @Override
    public SlotPayloadType type() {
        return SlotPayloadType.INT;
    }
}
