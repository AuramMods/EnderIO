package com.enderio.core.common.network;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import net.minecraft.core.BlockPos;

public record ServerboundCDataSlotUpdate(BlockPos pos, byte[] slotData) {

    // @formatter:off
    public static final PacketCodec<ServerboundCDataSlotUpdate> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.BLOCK_POS,
        ServerboundCDataSlotUpdate::pos,
        PacketCodecs.BYTE_ARRAY,
        ServerboundCDataSlotUpdate::slotData,
        ServerboundCDataSlotUpdate::new);
    // @formatter:on

}
