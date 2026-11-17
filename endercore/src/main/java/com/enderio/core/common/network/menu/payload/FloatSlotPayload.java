package com.enderio.core.common.network.menu.payload;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;

public class FloatSlotPayload implements SlotPayload {

    public static final PacketCodec<FloatSlotPayload> STREAM_CODEC = PacketCodecs.FLOAT
            .map(FloatSlotPayload::new, FloatSlotPayload::value)
            ;

    private final float value;

    public FloatSlotPayload(float value) {
        this.value = value;
    }

    public float value() {
        return value;
    }

    @Override
    public SlotPayloadType type() {
        return SlotPayloadType.FLOAT;
    }
}
