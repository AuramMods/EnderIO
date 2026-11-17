package com.enderio.enderio.mixin;

import com.enderio.enderio.client.content.conduits.ConduitFacadeRendering;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Set;

/** Append before native translucency sorting and buffer finalization, as NeoForge's section event does. */
@Mixin(targets = "net.minecraft.client.renderer.chunk.ChunkRenderDispatcher$RenderChunk$RebuildTask")
public abstract class ChunkRebuildTaskMixin {
    @Inject(method = "compile", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/RenderType;translucent()Lnet/minecraft/client/renderer/RenderType;", ordinal = 0),
        locals = LocalCapture.CAPTURE_FAILHARD, require = 1)
    private void enderio$appendFacades(float x, float y, float z, ChunkBufferBuilderPack buffers,
            CallbackInfoReturnable<?> callback, @Coerce Object results, int padding, BlockPos origin,
            BlockPos end, VisGraph visibility, RenderChunkRegion region, PoseStack poseStack,
            Set<RenderType> layers, RandomSource random, BlockRenderDispatcher dispatcher) {
        var facades = ConduitFacadeRendering.capture(((ChunkCompileTaskAccessor) this).enderio$getModelData(), SectionPos.of(origin));
        if (facades.isEmpty()) return;
        facades.render(region, poseStack, layer -> {
            var builder = buffers.builder(layer);
            if (layers.add(layer)) builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            return builder;
        });
    }
}
