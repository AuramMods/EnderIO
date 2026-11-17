package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.EnderIO;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ServerboundTimerFilterPacket(int ticks, int maxTicks) implements CustomPacketPayload {

    public static final Type<ServerboundTimerFilterPacket> TYPE = new Type<>(EnderIO.rl("timer_filter_packet"));

    public static final PacketCodec<ServerboundTimerFilterPacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.VAR_INT,
        ServerboundTimerFilterPacket::ticks,
        PacketCodecs.VAR_INT,
        ServerboundTimerFilterPacket::maxTicks,
        ServerboundTimerFilterPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
