package com.enderio.core.common.network.menu.payload;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;

public record LongSlotPayload(long value) implements SlotPayload {

    public static final PacketCodec<LongSlotPayload> STREAM_CODEC = PacketCodecs.VAR_LONG
            .map(LongSlotPayload::new, LongSlotPayload::value)
            ;

    @Override
    public SlotPayloadType type() {
        return SlotPayloadType.LONG;
    }
}
