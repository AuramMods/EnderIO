package com.enderio.enderio.foundation.network.packets;

import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

public record ServerboundDoubleChannelPacket(DyeColor channel1, DyeColor channel2) {

    public static final ResourceLocation ID = EnderIOAPI.rl("double_channel_packet");

    public static final PacketCodec<ServerboundDoubleChannelPacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.DYE_COLOR,
        ServerboundDoubleChannelPacket::channel1,
        PacketCodecs.DYE_COLOR,
        ServerboundDoubleChannelPacket::channel2,
        ServerboundDoubleChannelPacket::new
    );

}
