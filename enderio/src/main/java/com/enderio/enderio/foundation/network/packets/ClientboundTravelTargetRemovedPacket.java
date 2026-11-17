package com.enderio.enderio.foundation.network.packets;

import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.enderio.EnderIO;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClientboundTravelTargetRemovedPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<ClientboundTravelTargetRemovedPacket> TYPE = new Type<>(EnderIO.rl("remove_travel_target"));

    public static final PacketCodec<ClientboundTravelTargetRemovedPacket> STREAM_CODEC =
        PacketCodecs.BLOCK_POS.map(ClientboundTravelTargetRemovedPacket::new, ClientboundTravelTargetRemovedPacket::pos);

    public ClientboundTravelTargetRemovedPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
