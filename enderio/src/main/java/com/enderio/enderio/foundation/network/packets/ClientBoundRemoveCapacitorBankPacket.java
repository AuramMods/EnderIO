package com.enderio.enderio.foundation.network.packets;

import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.enderio.EnderIO;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record ClientBoundRemoveCapacitorBankPacket(UUID uuid) implements CustomPacketPayload{
    public static final CustomPacketPayload.Type<ClientBoundRemoveCapacitorBankPacket> TYPE = new CustomPacketPayload.Type<>(EnderIO.rl("remove_capacitor_bank"));

    public static final PacketCodec<ClientBoundRemoveCapacitorBankPacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.UUID,
        ClientBoundRemoveCapacitorBankPacket::uuid,
        ClientBoundRemoveCapacitorBankPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
