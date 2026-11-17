package com.enderio.enderio.client.foundation.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;

/** Changes opacity after native quad lighting/tinting while retaining all other vertex data. */
public final class FixedAlphaVertexConsumer extends VertexConsumerWrapper {
    private final int alpha;

    public FixedAlphaVertexConsumer(VertexConsumer parent, int alpha) {
        super(parent);
        if (alpha < 0 || alpha > 255) throw new IllegalArgumentException("Alpha must be in [0, 255]");
        this.alpha = alpha;
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        parent.color(r, g, b, alpha);
        return this;
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
        parent.defaultColor(r, g, b, alpha);
    }
}
