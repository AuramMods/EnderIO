package com.enderio.enderio.foundation.particle;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import com.enderio.core.common.network.codec.PacketCodec;
import org.jetbrains.annotations.NotNull;

public class RangeParticleType extends ParticleType<RangeParticleData> {

    public RangeParticleType() {
        super(false);
    }

    @NotNull
    @Override
    public MapCodec<RangeParticleData> codec() {
        return RangeParticleData.CODEC;
    }

    @Override
    public PacketCodec<RangeParticleData> streamCodec() {
        return RangeParticleData.STREAM_CODEC;
    }
}
