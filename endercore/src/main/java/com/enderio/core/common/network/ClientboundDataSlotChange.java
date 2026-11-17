package com.enderio.core.common.network;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import net.minecraft.core.BlockPos;

public record ClientboundDataSlotChange(BlockPos pos, byte[] updateData) {

    // @formatter:off
    public static final PacketCodec<ClientboundDataSlotChange> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.BLOCK_POS,
        ClientboundDataSlotChange::pos,
        PacketCodecs.BYTE_ARRAY,
        ClientboundDataSlotChange::updateData,
        ClientboundDataSlotChange::new);
    // @formatter:on

}
