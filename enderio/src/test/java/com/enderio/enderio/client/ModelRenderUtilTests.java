package com.enderio.enderio.client;

import com.enderio.enderio.client.foundation.model.ModelRenderUtil;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.QuadTransformers;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Uses native CPU vertex/image buffers only: no Minecraft instance, window or GL context. */
class ModelRenderUtilTests {
    @Test
    void transformedConduitQuadsExposeFacingOfFinishedGeometry() {
        try (var sprite = new TestSprite(16, 16)) {
            var rotations = List.of(Axis.XP.rotationDegrees(90), Axis.XN.rotationDegrees(90),
                Axis.ZP.rotationDegrees(90), Axis.ZN.rotationDegrees(90), Axis.ZP.rotationDegrees(180));
            for (var quaternion : rotations) {
                var rotation = new Transformation(null, quaternion, null, null).applyOrigin(new Vector3f(0.5f));
                for (Direction sourceFace : Direction.values()) {
                    var source = ModelRenderUtil.createQuad(
                        ModelRenderUtil.createQuadVerts(sourceFace, 7 / 16f, 9 / 16f, 9 / 16f), sprite);
                    var rotated = QuadTransformers.applying(rotation).process(source);
                    var expectedFacing = Direction.rotate(rotation.getMatrix(), sourceFace);

                    // Forge leaves the original facing on the transformed copy.
                    assertEquals(sourceFace, rotated.getDirection());
                    var finished = ModelRenderUtil.rebuildTransformedQuad(rotated);
                    assertNotSame(rotated, finished);
                    assertEquals(expectedFacing, finished.getDirection(), sourceFace + " face rotated by " + quaternion);
                    assertArrayEquals(rotated.getVertices(), finished.getVertices());
                    assertSame(rotated.getSprite(), finished.getSprite());
                    assertEquals(rotated.getTintIndex(), finished.getTintIndex());
                    assertEquals(rotated.isShade(), finished.isShade());
                    assertEquals(rotated.hasAmbientOcclusion(), finished.hasAmbientOcclusion());
                }
            }
        }
    }

    @Test
    void allSixFacesHaveOutwardNormalsAndCorrectBounds() {
        try (var sprite = new TestSprite(16, 16)) {
            for (Direction face : Direction.values()) {
                Vector3f[] vertices = ModelRenderUtil.createQuadVerts(face, 0.25f, 0.75f, 0.875f);
                BakedQuad quad = ModelRenderUtil.createQuad(vertices, sprite);
                assertEquals(face, quad.getDirection());
                assertEquals(4 * IQuadTransformer.STRIDE, quad.getVertices().length);
                for (int vertex = 0; vertex < 4; vertex++) {
                    int offset = vertex * IQuadTransformer.STRIDE;
                    for (int axis = 0; axis < 3; axis++) {
                        float coordinate = Float.intBitsToFloat(quad.getVertices()[offset + IQuadTransformer.POSITION + axis]);
                        int step = axis == 0 ? face.getStepX() : axis == 1 ? face.getStepY() : face.getStepZ();
                        if (step != 0) {
                            assertEquals(step > 0 ? 0.875f : 0.125f, coordinate);
                        } else {
                            assertTrue(coordinate == 0.25f || coordinate == 0.75f);
                        }
                        int normal = quad.getVertices()[offset + IQuadTransformer.NORMAL];
                        assertEquals(step * 127, (byte) (normal >> (axis * 8)));
                    }
                }
            }
        }
    }

    @Test
    void textureAtlasOffsetColorAlphaAndUntintedMetadataSurviveBaking() {
        try (var sprite = new TestSprite(16, 16)) {
            BakedQuad quad = ModelRenderUtil.createQuad(
                ModelRenderUtil.createQuadVerts(Direction.SOUTH, 0, 1, 1), sprite, 0x336699, 0.5f);
            assertSame(sprite, quad.getSprite());
            assertEquals(-1, quad.getTintIndex());
            assertFalse(quad.isTinted());
            assertFalse(quad.isShade());
            assertFalse(quad.hasAmbientOcclusion());
            // A 16x16 sprite at (32,64) in a 256x256 atlas spans its complete rectangle.
            assertUvs(quad, new float[][] {{0.125f, 0.25f}, {0.125f, 0.3125f},
                {0.1875f, 0.3125f}, {0.1875f, 0.25f}});
            for (int vertex = 0; vertex < 4; vertex++) {
                assertEquals(0x7F996633, quad.getVertices()[vertex * IQuadTransformer.STRIDE + IQuadTransformer.COLOR]);
            }
        }
    }

    @Test
    void rectangularTexturesRetainTheBaselinePixelScale() {
        try (var sprite = new TestSprite(32, 8)) {
            BakedQuad quad = ModelRenderUtil.createQuad(ModelRenderUtil.createQuadVerts(Direction.UP, 0, 1, 1), sprite);
            // The source helper uses width/16 and height/16 normalized UVs. On 1.20.1
            // this means 32 and 8 sixteenths: two U repeats and half of the V span.
            assertUvs(quad, new float[][] {{0.125f, 0.25f}, {0.125f, 0.265625f},
                {0.375f, 0.265625f}, {0.375f, 0.25f}});
        }
    }

    @Test
    void bakingDoesNotMutateInputVerticesOrShareQuadStorage() {
        try (var sprite = new TestSprite(16, 16)) {
            Vector3f[] vertices = ModelRenderUtil.createQuadVerts(Direction.WEST, 0, 1, 1);
            Vector3f[] original = java.util.Arrays.stream(vertices).map(Vector3f::new).toArray(Vector3f[]::new);
            BakedQuad first = ModelRenderUtil.createQuad(vertices, sprite);
            BakedQuad second = ModelRenderUtil.createQuad(vertices, sprite);
            assertArrayEquals(original, vertices);
            assertArrayEquals(first.getVertices(), second.getVertices());
            assertNotSame(first.getVertices(), second.getVertices());
            first.getVertices()[IQuadTransformer.COLOR] = 0;
            assertEquals(0xFFFFFFFF, second.getVertices()[IQuadTransformer.COLOR]);
        }
    }

    private static void assertUvs(BakedQuad quad, float[][] expected) {
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
            assertEquals(expected[vertex][0], Float.intBitsToFloat(quad.getVertices()[offset]), 0.000001f);
            assertEquals(expected[vertex][1], Float.intBitsToFloat(quad.getVertices()[offset + 1]), 0.000001f);
        }
    }

    private static final class TestSprite extends TextureAtlasSprite implements AutoCloseable {
        private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("enderio_test", "quad");

        TestSprite(int width, int height) {
            super(ID, new SpriteContents(ID, new FrameSize(width, height), new NativeImage(width, height, false),
                AnimationMetadataSection.EMPTY, null), 256, 256, 32, 64);
        }

        @Override
        public void close() {
            contents().close();
        }
    }
}
