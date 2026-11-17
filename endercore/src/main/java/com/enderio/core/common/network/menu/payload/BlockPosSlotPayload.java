package com.enderio.core.common.network.menu.payload;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import net.minecraft.core.BlockPos;

public record BlockPosSlotPayload(BlockPos value) implements SlotPayload {

    public static final PacketCodec<BlockPosSlotPayload> STREAM_CODEC = PacketCodecs.BLOCK_POS
            .map(BlockPosSlotPayload::new, BlockPosSlotPayload::value)
            ;

    @Override
    public SlotPayloadType type() {
        return SlotPayloadType.BLOCK_POS;
    }
}
