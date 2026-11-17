package com.enderio.enderio.foundation.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

public class RangeParticleType extends ParticleType<RangeParticleData> {
    public RangeParticleType() {
        super(false, new ParticleOptions.Deserializer<>() {
            @Override
            public RangeParticleData fromCommand(ParticleType<RangeParticleData> type, StringReader reader) throws CommandSyntaxException {
                reader.expect(' ');
                int range = reader.readInt();
                reader.expect(' ');
                return new RangeParticleData(range, reader.readString());
            }

            @Override
            public RangeParticleData fromNetwork(ParticleType<RangeParticleData> type, FriendlyByteBuf buffer) {
                return RangeParticleData.STREAM_CODEC.decode(buffer);
            }
        });
    }

    @Override
    public Codec<RangeParticleData> codec() {
        return RangeParticleData.CODEC.codec();
    }
}
