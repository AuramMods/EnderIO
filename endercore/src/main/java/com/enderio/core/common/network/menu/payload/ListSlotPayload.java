package com.enderio.core.common.network.menu.payload;

import com.enderio.core.common.network.codec.PacketCodec;

import java.util.List;

public record ListSlotPayload(List<SlotPayload> contents) implements SlotPayload {

    public static final PacketCodec<ListSlotPayload> STREAM_CODEC = SlotPayload.STREAM_CODEC
            .list()
            .map(ListSlotPayload::new, ListSlotPayload::contents);

    @Override
    public SlotPayloadType type() {
        return SlotPayloadType.LIST;
    }
}
