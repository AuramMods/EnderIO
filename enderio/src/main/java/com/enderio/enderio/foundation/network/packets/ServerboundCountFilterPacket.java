package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

public record ServerboundCountFilterPacket(int containerId, DyeColor channel, int maxCount) {

    public static final ResourceLocation ID = EnderIOAPI.rl("count_filter_packet");

    public static final PacketCodec<ServerboundCountFilterPacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.VAR_INT,
        ServerboundCountFilterPacket::containerId,
        PacketCodecs.DYE_COLOR,
        ServerboundCountFilterPacket::channel,
        PacketCodecs.VAR_INT,
        ServerboundCountFilterPacket::maxCount,
        ServerboundCountFilterPacket::new
    );

}
