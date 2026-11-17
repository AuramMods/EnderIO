package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import net.minecraft.nbt.CompoundTag;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;

public record ClientboundSyncTravelDataPacket(CompoundTag data) {
    public static final ResourceLocation ID = EnderIOAPI.rl("sync_travel_data");

    public static final PacketCodec<ClientboundSyncTravelDataPacket> STREAM_CODEC =
        PacketCodecs.COMPOUND_TAG.map(ClientboundSyncTravelDataPacket::new, ClientboundSyncTravelDataPacket::data);

}
