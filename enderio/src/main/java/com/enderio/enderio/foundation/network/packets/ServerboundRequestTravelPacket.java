package com.enderio.enderio.foundation.network.packets;

import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.enderio.api.EnderIOAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;

public record ServerboundRequestTravelPacket(BlockPos pos) {

    public static final ResourceLocation ID = EnderIOAPI.rl("request_travel");

    public static final PacketCodec<ServerboundRequestTravelPacket> STREAM_CODEC =
        PacketCodecs.BLOCK_POS.map(ServerboundRequestTravelPacket::new, ServerboundRequestTravelPacket::pos);

    public ServerboundRequestTravelPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos());
    }

}
