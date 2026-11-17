package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.enderio.foundation.souldata.FarmSoul;
import net.minecraft.network.FriendlyByteBuf;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public record ClientboundFarmStationSoulPacket(Map<ResourceLocation, FarmSoul.SoulData> map) {

    public static final ResourceLocation ID = EnderIOAPI.rl("farm_soul");

    public static final PacketCodec<ClientboundFarmStationSoulPacket> STREAM_CODEC = PacketCodec.composite(
            PacketCodec.map(HashMap::new, PacketCodecs.RESOURCE_LOCATION, FarmSoul.STREAM_CODEC),
            ClientboundFarmStationSoulPacket::map, ClientboundFarmStationSoulPacket::new);

    public ClientboundFarmStationSoulPacket(FriendlyByteBuf buf) {
        this(STREAM_CODEC.decode(buf).map());
    }

}
