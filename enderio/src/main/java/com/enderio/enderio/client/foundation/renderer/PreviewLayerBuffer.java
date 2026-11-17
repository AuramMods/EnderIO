package com.enderio.enderio.client.foundation.renderer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.jetbrains.annotations.Nullable;

/** Reusable CPU geometry and index-only sorting for a world preview layer. */
public final class PreviewLayerBuffer {
    private final BufferBuilder buffer;
    private final VertexFormat.Mode mode;
    private final VertexFormat format;
    @Nullable private BufferBuilder.SortState sortState;

    public PreviewLayerBuffer(VertexFormat.Mode mode, VertexFormat format, int capacity) {
        this.mode = mode;
        this.format = format;
        buffer = new BufferBuilder(capacity);
    }

    public BufferBuilder builder() {
        if (!buffer.building()) buffer.begin(mode, format);
        return buffer;
    }

    /** The caller must upload or release the returned native buffer before reusing it. */
    @Nullable
    public BufferBuilder.RenderedBuffer finish(@Nullable VertexSorting sorting) {
        sortState = null;
        if (!buffer.building()) return null;
        if (!buffer.isCurrentBatchEmpty() && sorting != null) {
            buffer.setQuadSorting(sorting);
            sortState = buffer.getSortState();
        }
        return buffer.endOrDiscardIfEmpty();
    }

    /** Re-sort existing geometry without re-uploading or rebuilding its vertices. */
    @Nullable
    public BufferBuilder.RenderedBuffer resort(VertexSorting sorting) {
        if (sortState == null) return null;
        buffer.begin(mode, format);
        buffer.restoreSortState(sortState);
        buffer.setQuadSorting(sorting);
        return buffer.end();
    }
}
