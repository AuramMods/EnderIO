package com.enderio.enderio.mixin;

import net.minecraft.core.BlockPos;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** The Forge-added model-data field belongs to the compile-task superclass. */
@Mixin(targets = "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$ChunkCompileTask")
public interface ChunkCompileTaskAccessor {
    @Accessor(value = "modelData", remap = false)
    Map<BlockPos, ModelData> enderio$getModelData();
}
