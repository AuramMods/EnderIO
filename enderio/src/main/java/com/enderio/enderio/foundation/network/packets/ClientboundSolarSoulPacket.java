package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.EnderIO;
import com.enderio.enderio.foundation.souldata.SolarSoul;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public record ClientboundSolarSoulPacket(Map<ResourceLocation, SolarSoul.SoulData> map) implements CustomPacketPayload {

    public static final Type<ClientboundSolarSoulPacket> TYPE = new Type<>(EnderIO.rl("solar_soul"));

    public static final PacketCodec<ClientboundSolarSoulPacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodec.map(HashMap::new, PacketCodecs.RESOURCE_LOCATION, SolarSoul.STREAM_CODEC),
        ClientboundSolarSoulPacket::map,
        ClientboundSolarSoulPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
