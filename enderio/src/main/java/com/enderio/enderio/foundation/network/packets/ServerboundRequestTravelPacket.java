package com.enderio.enderio.foundation.network.packets;

import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.enderio.EnderIO;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ServerboundRequestTravelPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<ServerboundRequestTravelPacket> TYPE = new Type<>(EnderIO.rl("request_travel"));

    public static final PacketCodec<ServerboundRequestTravelPacket> STREAM_CODEC =
        PacketCodecs.BLOCK_POS.map(ServerboundRequestTravelPacket::new, ServerboundRequestTravelPacket::pos);

    public ServerboundRequestTravelPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
