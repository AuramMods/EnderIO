package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.EnderIO;
import net.minecraft.core.BlockPos;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record ServerboundToggleMagnetPacket() implements CustomPacketPayload {

    public static final Type<ServerboundToggleMagnetPacket> TYPE = new Type<>(EnderIO.rl("toggle_magnet"));

    public static final PacketCodec<ServerboundToggleMagnetPacket> STREAM_CODEC =
        PacketCodec.unit(new ServerboundToggleMagnetPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

