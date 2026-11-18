package com.enderio.core.common.network.menu.payload;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import net.minecraft.nbt.CompoundTag;

public record CompoundTagSlotPayload(CompoundTag value) implements SlotPayload {
    public static final PacketCodec<CompoundTagSlotPayload> STREAM_CODEC = PacketCodecs.COMPOUND_TAG
        .map(CompoundTagSlotPayload::new, CompoundTagSlotPayload::value);

    @Override
    public SlotPayloadType type() {
        return SlotPayloadType.COMPOUND_TAG;
    }
}
