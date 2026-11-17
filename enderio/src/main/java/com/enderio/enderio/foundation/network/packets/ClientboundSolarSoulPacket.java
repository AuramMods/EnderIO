package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.enderio.foundation.souldata.SolarSoul;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public record ClientboundSolarSoulPacket(Map<ResourceLocation, SolarSoul.SoulData> map) {

    public static final ResourceLocation ID = EnderIOAPI.rl("solar_soul");

    public static final PacketCodec<ClientboundSolarSoulPacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodec.map(HashMap::new, PacketCodecs.RESOURCE_LOCATION, SolarSoul.STREAM_CODEC),
        ClientboundSolarSoulPacket::map,
        ClientboundSolarSoulPacket::new
    );

}
