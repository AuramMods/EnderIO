package com.enderio.core.common.network.menu.payload;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;

public record BoolSlotPayload(boolean value) implements SlotPayload {

    public static final PacketCodec<BoolSlotPayload> STREAM_CODEC = PacketCodecs.BOOL
            .map(BoolSlotPayload::new, BoolSlotPayload::value)
            ;

    @Override
    public SlotPayloadType type() {
        return SlotPayloadType.BOOL;
    }
}
