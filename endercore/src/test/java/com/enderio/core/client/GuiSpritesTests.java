package com.enderio.core.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class GuiSpritesTests {
    @Test
    void croppedBarUsesAtlasOffsetsAndTheCurrentPose() {
        try (var sprite = new TestSprite(16, 16)) {
            var builder = begin();
            GuiSprites.writeRegion(builder, new Matrix4f().translation(3, 4, 5), sprite,
                16, 16, 4, 8, 10, 20, 8, 8);
            var result = builder.end();
            try {
                assertEquals(4, result.drawState().vertexCount());
                var data = result.vertexBuffer().order(ByteOrder.nativeOrder());
                float[][] expected = {{13, 24, 5, 36f / 256, 72f / 256}, {13, 32, 5, 36f / 256, 80f / 256},
                    {21, 32, 5, 44f / 256, 80f / 256}, {21, 24, 5, 44f / 256, 72f / 256}};
                for (float[] vertex : expected) for (float component : vertex) assertEquals(component, data.getFloat(), 0.000001f);
            } finally { result.release(); }
        }
    }

    @Test
    void logicalDimensionsAreIndependentOfTextureResolution() {
        try (var sprite = new TestSprite(32, 8)) {
            var builder = begin();
            GuiSprites.writeRegion(builder, new Matrix4f(), sprite, 16, 16, 8, 4, 0, 0, 8, 12);
            var result = builder.end();
            try {
                var data = result.vertexBuffer().order(ByteOrder.nativeOrder());
                assertEquals(48f / 256, data.getFloat(12));
                assertEquals(66f / 256, data.getFloat(16));
                assertEquals(64f / 256, data.getFloat(2 * 20 + 12));
                assertEquals(72f / 256, data.getFloat(2 * 20 + 16));
            } finally { result.release(); }
        }
    }

    @Test
    void emptyOrNegativeFillDoesNotEmitBackwardsGeometry() {
        try (var sprite = new TestSprite(16, 16)) {
            var builder = begin();
            for (int[] size : new int[][] {{0, 16}, {-1, 16}, {16, 0}, {16, -1}}) {
                GuiSprites.writeRegion(builder, new Matrix4f(), sprite, 16, 16, 0, 0, 0, 0, size[0], size[1]);
            }
            assertNull(builder.endOrDiscardIfEmpty());
        }
    }

    private static BufferBuilder begin() {
        var builder = new BufferBuilder(64);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        return builder;
    }

    private static final class TestSprite extends TextureAtlasSprite implements AutoCloseable {
        private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("endercore_test", "gui_crop");
        TestSprite(int width, int height) {
            super(ID, new SpriteContents(ID, new FrameSize(width, height), new NativeImage(width, height, false),
                AnimationMetadataSection.EMPTY, null), 256, 256, 32, 64);
        }
        @Override public void close() { contents().close(); }
    }
}
