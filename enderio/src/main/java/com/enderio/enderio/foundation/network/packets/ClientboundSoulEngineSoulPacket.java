package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.enderio.foundation.souldata.EngineSoul;
import net.minecraft.network.FriendlyByteBuf;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public record ClientboundSoulEngineSoulPacket(Map<ResourceLocation, EngineSoul.SoulData> map)
    {

    public static final ResourceLocation ID = EnderIOAPI.rl("soul_engine_soul");

    public static final PacketCodec<ClientboundSoulEngineSoulPacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodec.map(HashMap::new, PacketCodecs.RESOURCE_LOCATION, EngineSoul.STREAM_CODEC),
        ClientboundSoulEngineSoulPacket::map,
        ClientboundSoulEngineSoulPacket::new
    );

    public ClientboundSoulEngineSoulPacket(FriendlyByteBuf buf) {
        this(
            buf.readMap(FriendlyByteBuf::readResourceLocation, buff ->
                new EngineSoul.SoulData(buff.readResourceLocation(), buff.readUtf(), buff.readInt(), buff.readInt())
            )
        );
    }

}
