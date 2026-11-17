package com.enderio.enderio.foundation.network.packets;

import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.enderio.EnderIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ServerboundCycleIOConfigPacket(BlockPos pos, Direction side) implements CustomPacketPayload {

    public static final Type<ServerboundCycleIOConfigPacket> TYPE = new Type<>(EnderIO.rl("cycle_io_config"));

    public static final PacketCodec<ServerboundCycleIOConfigPacket> STREAM_CODEC = PacketCodec.composite(PacketCodecs.BLOCK_POS,
            ServerboundCycleIOConfigPacket::pos, PacketCodecs.DIRECTION, ServerboundCycleIOConfigPacket::side, ServerboundCycleIOConfigPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
