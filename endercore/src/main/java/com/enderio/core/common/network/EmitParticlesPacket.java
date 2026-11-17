package com.enderio.core.common.network;

import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;

import java.util.ArrayList;
import java.util.List;

// TODO: Not a big fan of this..
public record EmitParticlesPacket(List<EmitParticlePacket> particles) {

    // @formatter:off
    public static final PacketCodec<EmitParticlesPacket> STREAM_CODEC =
        EmitParticlePacket.STREAM_CODEC.list()
            .map(EmitParticlesPacket::new, EmitParticlesPacket::particles);
    // @formatter:on

    public EmitParticlesPacket() {
        this(new ArrayList<>());
    }


    public void add(EmitParticlePacket particlePacket) {
        particles.add(particlePacket);
    }

    public void add(ParticleOptions type, double x, double y, double z) {
        add(type, x, y, z, 0, 0, 0);
    }

    public void add(ParticleOptions type, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        add(new EmitParticlePacket(type, x, y, z, xSpeed, ySpeed, zSpeed));
    }

    public void add(BlockPos pos, ParticleOptions type) {
        add(new EmitParticlePacket(type, pos));
    }
}
