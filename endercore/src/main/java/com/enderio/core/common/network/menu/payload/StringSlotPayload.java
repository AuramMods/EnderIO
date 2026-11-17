package com.enderio.core.common.network.menu.payload;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;

public record StringSlotPayload(String value) implements SlotPayload {

    public static final PacketCodec<StringSlotPayload> STREAM_CODEC = PacketCodecs.STRING_UTF8
            .map(StringSlotPayload::new, StringSlotPayload::value)
            ;

    @Override
    public SlotPayloadType type() {
        return SlotPayloadType.STRING;
    }
}
