package com.enderio.enderio.client;

import com.enderio.enderio.client.foundation.renderer.PreviewLayerBuffer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class PreviewLayerBufferTests {
    @Test
    void transparentQuadsResortFromBothSidesWithoutReplacingVertices() {
        var layer = layer();
        quad(layer.builder(), 1);
        quad(layer.builder(), 9);
        var geometry = layer.finish(VertexSorting.byDistance(0, 0, 0));
        assertNotNull(geometry);
        try {
            assertFalse(geometry.drawState().indexOnly());
            assertEquals(8 * 12, geometry.vertexBuffer().remaining());
            assertFirstQuad(geometry, 4);
        } finally { geometry.release(); }

        for (int cameraX : new int[] {20, 0, 20, 0}) {
            var indices = layer.resort(VertexSorting.byDistance(cameraX, 0, 0));
            assertNotNull(indices);
            try {
                assertTrue(indices.drawState().indexOnly());
                // The unused native vertexBuffer view still spans the original vertex count.
                // DrawState bounds the actual payload to indices and tells VertexBuffer to retain its vertices.
                assertEquals(12 * Short.BYTES, indices.drawState().bufferSize());
                assertEquals(12 * Short.BYTES, indices.indexBuffer().remaining());
                assertEquals(8, indices.drawState().vertexCount());
                assertFirstQuad(indices, cameraX == 0 ? 4 : 0);
            } finally { indices.release(); }
        }
    }

    @Test
    void rebuildingAnEmptyLayerClearsItsOldSortState() {
        var layer = layer();
        quad(layer.builder(), 1);
        layer.finish(VertexSorting.byDistance(0, 0, 0)).release();
        layer.builder();
        assertNull(layer.finish(VertexSorting.byDistance(0, 0, 0)));
        assertNull(layer.resort(VertexSorting.byDistance(20, 0, 0)));
        assertNull(layer.finish(null));
    }

    @Test
    void rebuiltOpaqueGeometryUsesSequentialIndicesAndNoStaleTransparency() {
        var layer = layer();
        quad(layer.builder(), 9);
        layer.finish(VertexSorting.byDistance(0, 0, 0)).release();
        quad(layer.builder(), 2);
        var geometry = layer.finish(null);
        assertNotNull(geometry);
        try {
            assertFalse(geometry.drawState().indexOnly());
            assertTrue(geometry.drawState().sequentialIndex());
            assertEquals(4, geometry.drawState().vertexCount());
        } finally { geometry.release(); }
        assertNull(layer.resort(VertexSorting.byDistance(0, 0, 0)));
    }

    private static PreviewLayerBuffer layer() {
        return new PreviewLayerBuffer(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION, 64);
    }

    private static void quad(BufferBuilder builder, float centerX) {
        builder.vertex(centerX - 0.5f, 0, 0).endVertex();
        builder.vertex(centerX - 0.5f, 1, 0).endVertex();
        builder.vertex(centerX + 0.5f, 1, 0).endVertex();
        builder.vertex(centerX + 0.5f, 0, 0).endVertex();
    }

    private static void assertFirstQuad(BufferBuilder.RenderedBuffer result, int firstVertex) {
        var indices = result.indexBuffer().order(ByteOrder.nativeOrder()).asShortBuffer();
        assertEquals(12, result.drawState().indexCount());
        assertEquals(firstVertex, Short.toUnsignedInt(indices.get(0)));
        assertEquals(firstVertex == 0 ? 4 : 0, Short.toUnsignedInt(indices.get(6)));
        for (int i = 0; i < 12; i++) assertTrue(Short.toUnsignedInt(indices.get(i)) < 8);
    }
}
