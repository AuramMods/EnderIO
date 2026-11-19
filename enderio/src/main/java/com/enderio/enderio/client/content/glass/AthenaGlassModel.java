package com.enderio.enderio.client.content.glass;

import com.enderio.enderio.init.EIOBlocks;
import earth.terrarium.athena.api.client.forge.AthenaBakedModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/** Athena's 1.20.1 Forge baker does not apply the glass blockstate's layer or tint index. */
public final class AthenaGlassModel extends BakedModelWrapper<AthenaBakedModel> {
    private static final ChunkRenderTypeSet LAYERS = ChunkRenderTypeSet.of(RenderType.cutout());

    private AthenaGlassModel(AthenaBakedModel model) {
        super(model);
    }

    public static void applyGlassRendering(Map<ResourceLocation, BakedModel> models) {
        for (var family : EIOBlocks.GLASS_BLOCKS.values()) {
            family.getAllBlocks().forEach(entry -> {
                var id = BlockModelShaper.stateToModelLocation(entry.get().defaultBlockState());
                models.computeIfPresent(id, (key, model) -> model instanceof AthenaBakedModel athena
                    ? new AthenaGlassModel(athena) : model);
            });
        }
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return LAYERS;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                   ModelData data, @Nullable RenderType layer) {
        if (layer != null && layer != RenderType.cutout()) return List.of();
        return super.getQuads(state, side, random, data, layer).stream()
            .map(quad -> new BakedQuad(quad.getVertices(), 0, quad.getDirection(), quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion()))
            .toList();
    }
}
