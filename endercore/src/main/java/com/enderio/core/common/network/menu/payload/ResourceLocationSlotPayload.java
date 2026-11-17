package com.enderio.core.common.network.menu.payload;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import net.minecraft.resources.ResourceLocation;

public record ResourceLocationSlotPayload(ResourceLocation value) implements SlotPayload {

    public static final PacketCodec<ResourceLocationSlotPayload> STREAM_CODEC = PacketCodecs.RESOURCE_LOCATION
            .map(ResourceLocationSlotPayload::new, ResourceLocationSlotPayload::value)
            ;

    @Override
    public SlotPayloadType type() {
        return SlotPayloadType.RESOURCE_LOCATION;
    }
}
