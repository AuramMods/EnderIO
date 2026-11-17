package com.enderio.enderio.client;

import com.enderio.enderio.client.foundation.model.ModelRenderUtil;
import com.enderio.enderio.client.foundation.renderer.FixedAlphaVertexConsumer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class FixedAlphaVertexConsumerTests {
    @Test
    void nativeLitQuadEmissionChangesOnlyAlphaAndDoesNotMutateSharedQuads() {
        try (var sprite = new TestSprite()) {
            var quad = ModelRenderUtil.createQuad(ModelRenderUtil.createQuadVerts(Direction.SOUTH, 0, 1, 1),
                sprite, 0x6699CC, 0.6f);
            int[] original = quad.getVertices().clone();
            var normal = new BufferBuilder(256);
            var transparent = new BufferBuilder(256);
            normal.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            transparent.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            var wrapper = new FixedAlphaVertexConsumer(transparent, 85);
            var pose = new PoseStack();
            pose.translate(3, 5, 7);
            float[] brightness = {0.25f, 0.5f, 0.75f, 1};
            int[] lights = {0, 0x00F00000, 0x000000F0, 0x00F000F0};
            normal.putBulkData(pose.last(), quad, brightness, 0.5f, 0.75f, 1, lights, 0, true);
            wrapper.putBulkData(pose.last(), quad, brightness, 0.5f, 0.75f, 1, lights, 0, true);
            var reference = normal.end();
            var result = transparent.end();
            try {
                ByteBuffer expected = reference.vertexBuffer();
                ByteBuffer actual = result.vertexBuffer();
                assertEquals(4, result.drawState().vertexCount());
                assertEquals(expected.remaining(), actual.remaining());
                int stride = DefaultVertexFormat.BLOCK.getVertexSize();
                for (int i = 0; i < expected.remaining(); i++) {
                    if (i % stride == 15) assertEquals(85, Byte.toUnsignedInt(actual.get(i)));
                    else assertEquals(expected.get(i), actual.get(i), "Vertex byte " + i);
                }
                assertArrayEquals(original, quad.getVertices());
            } finally {
                reference.release();
                result.release();
            }
        }
    }

    @Test
    void fluentAndDefaultColorPathsRespectTransparentAndOpaqueEndpoints() {
        for (int alpha : new int[] {0, 85, 255}) {
            for (boolean defaultColor : new boolean[] {false, true}) {
                var builder = new BufferBuilder(64);
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                var wrapper = new FixedAlphaVertexConsumer(builder, alpha);
                if (defaultColor) wrapper.defaultColor(13, 29, 97, 123);
                for (int i = 0; i < 4; i++) {
                    var vertex = wrapper.vertex(i & 1, i >> 1, 3);
                    assertSame(wrapper, vertex);
                    if (!defaultColor) assertSame(wrapper, vertex.color(13, 29, 97, 123));
                    vertex.endVertex();
                }
                wrapper.unsetDefaultColor();
                var result = builder.end();
                try {
                    var bytes = result.vertexBuffer();
                    assertEquals(4, result.drawState().vertexCount());
                    for (int i = 0; i < 4; i++) {
                        int offset = i * DefaultVertexFormat.POSITION_COLOR.getVertexSize();
                        assertEquals(13, Byte.toUnsignedInt(bytes.get(offset + 12)));
                        assertEquals(29, Byte.toUnsignedInt(bytes.get(offset + 13)));
                        assertEquals(97, Byte.toUnsignedInt(bytes.get(offset + 14)));
                        assertEquals(alpha, Byte.toUnsignedInt(bytes.get(offset + 15)));
                    }
                } finally { result.release(); }
            }
        }
    }

    @Test
    void invalidAlphaCannotWrapToAnUnintendedOpacity() {
        var builder = new BufferBuilder(64);
        assertThrows(IllegalArgumentException.class, () -> new FixedAlphaVertexConsumer(builder, -1));
        assertThrows(IllegalArgumentException.class, () -> new FixedAlphaVertexConsumer(builder, 256));
    }

    private static final class TestSprite extends TextureAtlasSprite implements AutoCloseable {
        private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("enderio_test", "facade");
        TestSprite() {
            super(ID, new SpriteContents(ID, new FrameSize(16, 16), new NativeImage(16, 16, false),
                AnimationMetadataSection.EMPTY, null), 256, 256, 32, 64);
        }
        @Override public void close() { contents().close(); }
    }
}
