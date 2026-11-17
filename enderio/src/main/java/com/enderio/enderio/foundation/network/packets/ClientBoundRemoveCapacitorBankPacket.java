package com.enderio.enderio.foundation.network.packets;

import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ClientBoundRemoveCapacitorBankPacket(UUID uuid){
    public static final ResourceLocation ID = EnderIOAPI.rl("remove_capacitor_bank");

    public static final PacketCodec<ClientBoundRemoveCapacitorBankPacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.UUID,
        ClientBoundRemoveCapacitorBankPacket::uuid,
        ClientBoundRemoveCapacitorBankPacket::new
    );

}
