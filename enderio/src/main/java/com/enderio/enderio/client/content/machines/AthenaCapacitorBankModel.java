package com.enderio.enderio.client.content.machines;

import com.enderio.enderio.init.EIOBlocks;
import earth.terrarium.athena.api.client.forge.AthenaBakedModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Athena replaces the whole blockstate model, including the ordinary bank's I/O overlay. */
public final class AthenaCapacitorBankModel extends BakedModelWrapper<AthenaBakedModel> {
    private final IOOverlayBakedModel overlay = new IOOverlayBakedModel(BlockModelRotation.X0_Y0);

    private AthenaCapacitorBankModel(AthenaBakedModel model) {
        super(model);
    }

    public static void addOverlays(Map<ResourceLocation, BakedModel> models) {
        for (var bank : EIOBlocks.CAPACITOR_BANKS.values()) {
            var id = BlockModelShaper.stateToModelLocation(bank.get().defaultBlockState());
            models.computeIfPresent(id, (key, model) -> model instanceof AthenaBakedModel athena
                ? new AthenaCapacitorBankModel(athena) : model);
        }
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return ChunkRenderTypeSet.union(super.getRenderTypes(state, random, data), overlay.getRenderTypes(state, random, data));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                   ModelData data, @Nullable RenderType layer) {
        if (layer == RenderType.cutout()) {
            return side == null ? overlay.getQuads(state, null, random, data, layer) : List.of();
        }
        var base = super.getQuads(state, side, random, data, layer);
        if (layer != null || side != null) return base;
        var quads = new ArrayList<>(base);
        quads.addAll(overlay.getQuads(state, null, random, data, null));
        return quads;
    }
}
