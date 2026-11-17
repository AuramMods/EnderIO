package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.EnderIO;
import net.minecraft.core.BlockPos;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record ServerboundRequestShortTravelPacket() implements CustomPacketPayload {

    public static final Type<ServerboundRequestShortTravelPacket> TYPE = new Type<>(EnderIO.rl("request_short_travel"));

    public static final PacketCodec<ServerboundRequestShortTravelPacket> STREAM_CODEC =
        PacketCodec.unit(new ServerboundRequestShortTravelPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
