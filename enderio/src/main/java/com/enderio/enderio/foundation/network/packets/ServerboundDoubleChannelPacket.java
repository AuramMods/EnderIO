package com.enderio.enderio.foundation.network.packets;

import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.enderio.EnderIO;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.DyeColor;

public record ServerboundDoubleChannelPacket(DyeColor channel1, DyeColor channel2) implements CustomPacketPayload {

    public static final Type<ServerboundDoubleChannelPacket> TYPE = new Type<>(EnderIO.rl("double_channel_packet"));

    public static final PacketCodec<ServerboundDoubleChannelPacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.DYE_COLOR,
        ServerboundDoubleChannelPacket::channel1,
        PacketCodecs.DYE_COLOR,
        ServerboundDoubleChannelPacket::channel2,
        ServerboundDoubleChannelPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
