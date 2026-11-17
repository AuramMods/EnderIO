package com.enderio.enderio.foundation.particle;

import com.enderio.enderio.init.EIOParticles;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;

public record RangeParticleData(int range, String color) implements ParticleOptions {

    public static final MapCodec<RangeParticleData> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            Codec.INT.fieldOf("range").forGetter(RangeParticleData::range),
            Codec.STRING.fieldOf("color").forGetter(RangeParticleData::color)
        )
        .apply(instance, RangeParticleData::new)
    );

    public static final PacketCodec<RangeParticleData> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.INT,
        RangeParticleData::range,
        PacketCodecs.STRING_UTF8,
        RangeParticleData::color,
        RangeParticleData::new
    );

    @Override
    public ParticleType<?> getType() {
        return EIOParticles.RANGE_PARTICLE.get();
    }
}
