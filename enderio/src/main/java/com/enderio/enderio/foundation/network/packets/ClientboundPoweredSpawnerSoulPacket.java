package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.enderio.content.machines.powered_spawner.MobSpawnMode;
import com.enderio.enderio.foundation.souldata.SpawnerSoul;
import net.minecraft.network.FriendlyByteBuf;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public record ClientboundPoweredSpawnerSoulPacket(Map<ResourceLocation, SpawnerSoul.SoulData> map) {

    public static final ResourceLocation ID = EnderIOAPI.rl("powered_spawner_soul");

    public static final PacketCodec<ClientboundPoweredSpawnerSoulPacket> STREAM_CODEC = PacketCodec.composite(
            PacketCodec.map(HashMap::new, PacketCodecs.RESOURCE_LOCATION, SpawnerSoul.STREAM_CODEC),
            ClientboundPoweredSpawnerSoulPacket::map, ClientboundPoweredSpawnerSoulPacket::new);

    public ClientboundPoweredSpawnerSoulPacket(FriendlyByteBuf buf) {
        this(buf.readMap(FriendlyByteBuf::readResourceLocation,
                buff -> new SpawnerSoul.SoulData(buff.readResourceLocation(), buff.readInt(),
                        buff.readEnum(MobSpawnMode.class))));
    }

}
