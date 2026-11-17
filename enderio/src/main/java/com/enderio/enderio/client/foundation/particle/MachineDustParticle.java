package com.enderio.enderio.client.foundation.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.BaseAshSmokeParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/** Rising dust for the SAG mill on the native 1.20.1 particle renderer. */
public final class MachineDustParticle extends BaseAshSmokeParticle {
    private MachineDustParticle(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z, 0.7F, 0.6F, 0.7F, xSpeed, ySpeed + 0.15F, zSpeed,
            1.0F, sprites, 0.5F, 7, 0.5F, false);
        float shade = (float) Math.random() * 0.2F;
        setColor(186 / 255.0F - shade, 177 / 255.0F - shade, 194 / 255.0F - shade);
    }

    @Override
    public void tick() {
        gravity *= 0.88F;
        friction *= 0.92F;
        super.tick();
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed) {
            return new MachineDustParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
