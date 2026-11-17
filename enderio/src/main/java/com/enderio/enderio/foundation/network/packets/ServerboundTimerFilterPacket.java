package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;

public record ServerboundTimerFilterPacket(int ticks, int maxTicks) {

    public static final ResourceLocation ID = EnderIOAPI.rl("timer_filter_packet");

    public static final PacketCodec<ServerboundTimerFilterPacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.VAR_INT,
        ServerboundTimerFilterPacket::ticks,
        PacketCodecs.VAR_INT,
        ServerboundTimerFilterPacket::maxTicks,
        ServerboundTimerFilterPacket::new
    );

}
