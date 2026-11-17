package com.enderio.core.common.network.menu.payload;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import net.minecraft.world.item.ItemStack;

public record ItemStackSlotPayload(ItemStack value) implements SlotPayload {

    public static final PacketCodec<ItemStackSlotPayload> STREAM_CODEC = PacketCodecs.ITEM_STACK
            .map(ItemStackSlotPayload::new, ItemStackSlotPayload::value);

    @Override
    public SlotPayloadType type() {
        return SlotPayloadType.ITEM_STACK;
    }
}
