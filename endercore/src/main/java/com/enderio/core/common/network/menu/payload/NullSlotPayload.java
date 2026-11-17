package com.enderio.core.common.network.menu.payload;

import com.enderio.core.common.network.codec.PacketCodec;

public record NullSlotPayload() implements SlotPayload {

    public static final PacketCodec<NullSlotPayload> STREAM_CODEC = PacketCodec.of((buf, val) -> {
    }, (buf) -> new NullSlotPayload());

    @Override
    public SlotPayloadType type() {
        return SlotPayloadType.NULL;
    }
}
