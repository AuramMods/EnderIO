package com.enderio.core.common.network.menu.payload;

import com.enderio.core.common.network.codec.PacketCodec;

public record PairSlotPayload(SlotPayload left, SlotPayload right) implements SlotPayload {

    public static final PacketCodec<PairSlotPayload> STREAM_CODEC = PacketCodec.composite(
            SlotPayload.STREAM_CODEC, PairSlotPayload::left, SlotPayload.STREAM_CODEC, PairSlotPayload::right,
            PairSlotPayload::new);

    @Override
    public SlotPayloadType type() {
        return SlotPayloadType.PAIR;
    }
}
