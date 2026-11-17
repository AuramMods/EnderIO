package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

public record ServerboundCountFilterPacket(DyeColor channel1, int maxCount, int count, boolean active) {

    public static final ResourceLocation ID = EnderIOAPI.rl("count_filter_packet");

    public static final PacketCodec<ServerboundCountFilterPacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.DYE_COLOR,
        ServerboundCountFilterPacket::channel1,
        PacketCodecs.VAR_INT,
        ServerboundCountFilterPacket::maxCount,
        PacketCodecs.VAR_INT,
        ServerboundCountFilterPacket::count,
        PacketCodecs.BOOL,
        ServerboundCountFilterPacket::active,
        ServerboundCountFilterPacket::new
    );

}
