package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import net.minecraft.core.BlockPos;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record ServerboundToggleMagnetPacket() {

    public static final ResourceLocation ID = EnderIOAPI.rl("toggle_magnet");

    public static final PacketCodec<ServerboundToggleMagnetPacket> STREAM_CODEC =
        PacketCodec.unit(new ServerboundToggleMagnetPacket());

}

