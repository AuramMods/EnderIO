package com.enderio.enderio.foundation.network.packets;

import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.enderio.api.EnderIOAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;

public record ClientboundTravelTargetRemovedPacket(BlockPos pos) {

    public static final ResourceLocation ID = EnderIOAPI.rl("remove_travel_target");

    public static final PacketCodec<ClientboundTravelTargetRemovedPacket> STREAM_CODEC =
        PacketCodecs.BLOCK_POS.map(ClientboundTravelTargetRemovedPacket::new, ClientboundTravelTargetRemovedPacket::pos);

    public ClientboundTravelTargetRemovedPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos());
    }

}
