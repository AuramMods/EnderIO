package com.enderio.core.common.network;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;

public record EmitParticlePacket(ParticleOptions particleOptions, double x, double y, double z, double xSpeed,
        double ySpeed, double zSpeed) {

    public static final PacketCodec<EmitParticlePacket> STREAM_CODEC = PacketCodec.of((buffer, packet) -> {
        PacketCodecs.PARTICLE.encode(buffer, packet.particleOptions());
        buffer.writeDouble(packet.x());
        buffer.writeDouble(packet.y());
        buffer.writeDouble(packet.z());
        buffer.writeDouble(packet.xSpeed());
        buffer.writeDouble(packet.ySpeed());
        buffer.writeDouble(packet.zSpeed());
    }, buffer -> new EmitParticlePacket(PacketCodecs.PARTICLE.decode(buffer), buffer.readDouble(),
        buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));

    public EmitParticlePacket(ParticleOptions type, BlockPos pos) {
        this(type, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0.0, 0.0, 0.0);
    }

    public EmitParticlePacket(ParticleOptions type, BlockPos pos, double xSpeed, double ySpeed, double zSpeed) {
        this(type, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, xSpeed, ySpeed, zSpeed);
    }

}
