package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.EnderIO;
import net.minecraft.nbt.CompoundTag;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClientboundSyncTravelDataPacket(CompoundTag data) implements CustomPacketPayload {
    public static final Type<ClientboundSyncTravelDataPacket> TYPE = new Type<>(EnderIO.rl("sync_travel_data"));

    public static final PacketCodec<ClientboundSyncTravelDataPacket> STREAM_CODEC =
        PacketCodecs.COMPOUND_TAG.map(ClientboundSyncTravelDataPacket::new, ClientboundSyncTravelDataPacket::data);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
