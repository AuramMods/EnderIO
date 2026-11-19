package com.enderio.enderio.client.content.conduits;

import com.enderio.enderio.client.content.conduits.model.bundle.ConduitBundleRenderState;
import com.enderio.enderio.client.content.conduits.model.facades.ClientFacadeVisibility;
import com.enderio.enderio.client.foundation.renderer.FixedAlphaVertexConsumer;
import com.enderio.enderio.compat.ModCompatHelper;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelDataManager;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Additional chunk geometry shared by the Forge and Embeddium chunk compilers. */
public final class ConduitFacadeRendering {
    private static final ThreadLocal<RandomSource> RANDOM = ThreadLocal
        .withInitial(() -> new SingleThreadedRandomSource(42L));

    private ConduitFacadeRendering() {}

    /** Capture the compiler's model-data snapshot, never a mutable client-world facade index. */
    public static Facades capture(Map<BlockPos, ModelData> modelData, SectionPos section) {
        Map<BlockPos, Facade> facades = new HashMap<>();
        modelData.forEach((pos, data) -> {
            if (SectionPos.asLong(pos) != section.asLong()) return;
            var bundle = data.get(ConduitBundleRenderState.PROPERTY);
            if (bundle != null && bundle.hasFacade()) {
                facades.put(pos.immutable(), new Facade(bundle.facade(), data));
            }
        });
        return new Facades(Map.copyOf(facades), ClientFacadeVisibility.areFacadesVisible());
    }

    private record Facade(BlockState state, ModelData data) {}

    public static final class Facades {
        private final Map<BlockPos, Facade> facades;
        private final boolean opaque;

        private Facades(Map<BlockPos, Facade> facades, boolean opaque) {
            this.facades = facades;
            this.opaque = opaque;
        }

        public boolean isEmpty() {
            return facades.isEmpty();
        }

        public void render(BlockAndTintGetter region, PoseStack poseStack,
                Function<RenderType, VertexConsumer> buffers) {
            // Preserve GH-1062: only ghost facades are suppressed with an active shader pack.
            if (!opaque && ModCompatHelper.hasIris() && ShaderBridge.isShaderPackInUse()) return;

            var dispatcher = Minecraft.getInstance().getBlockRenderer();
            var random = RANDOM.get();
            VertexConsumer ghost = opaque ? null : new FixedAlphaVertexConsumer(buffers.apply(RenderType.translucent()), 85);
            var tessellationRegion = withFacadeNeighbors(region);

            for (var entry : facades.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState state = entry.getValue().state();
                var model = dispatcher.getBlockModel(state);
                var data = model.getModelData(region, pos, state, entry.getValue().data());
                random.setSeed(42L);
                poseStack.pushPose();
                try {
                    poseStack.translate(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
                    for (var layer : model.getRenderTypes(state, random, data)) {
                        // Pass the actual facade state and original layer to the model renderer.
                        // A conduit-model quad merger would change tinting, AO and face culling.
                        var consumer = opaque ? buffers.apply(layer) : ghost;
                        dispatcher.getModelRenderer().tesselateBlock(tessellationRegion, model, state, pos, poseStack, consumer,
                            true, random, 42L, OverlayTexture.NO_OVERLAY, data, layer);
                    }
                } finally {
                    poseStack.popPose();
                }
            }
        }

        private BlockAndTintGetter withFacadeNeighbors(BlockAndTintGetter region) {
            Map<BlockPos, BlockState> appearances = new HashMap<>();
            facades.forEach((pos, facade) -> {
                for (Direction side : Direction.values()) {
                    BlockPos neighbor = pos.relative(side);
                    var captured = facades.get(neighbor);
                    if (captured != null) {
                        appearances.put(neighbor, captured.state());
                        continue;
                    }
                    var state = region.getBlockState(neighbor);
                    if (state.getBlock() instanceof ConduitBundleBlock) {
                        // The render region also carries block entities from sections outside this compile task.
                        var appearance = state.getAppearance(region, neighbor, side.getOpposite(), facade.state(), pos);
                        if (state != appearance) appearances.put(neighbor, appearance);
                    }
                }
            });
            return appearances.isEmpty() ? region : new FacadeRenderRegion(region, appearances);
        }
    }

    /** Native face culling reads block states, so present each adjoining bundle's painted surface. */
    private record FacadeRenderRegion(BlockAndTintGetter region, Map<BlockPos, BlockState> appearances) implements BlockAndTintGetter {
        @Override
        public BlockState getBlockState(BlockPos pos) {
            var appearance = appearances.get(pos);
            return appearance == null ? region.getBlockState(pos) : appearance;
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return region.getBlockEntity(pos);
        }

        @Override
        public @Nullable BlockEntity getExistingBlockEntity(BlockPos pos) {
            return region.getExistingBlockEntity(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return region.getFluidState(pos);
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return region.getBlockTint(pos, resolver);
        }

        @Override
        public float getShade(Direction side, boolean shade) {
            return region.getShade(side, shade);
        }

        @Override
        public float getShade(float normalX, float normalY, float normalZ, boolean shade) {
            return region.getShade(normalX, normalY, normalZ, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return region.getLightEngine();
        }

        @Override
        public int getLightEmission(BlockPos pos) {
            return region.getLightEmission(pos);
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return region.getBrightness(layer, pos);
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            return region.getRawBrightness(pos, amount);
        }

        @Override
        public int getHeight() {
            return region.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return region.getMinBuildHeight();
        }

        @Override
        public @Nullable ModelDataManager getModelDataManager() {
            return region.getModelDataManager();
        }
    }

    /** Keep optional shader API linkage outside the path used without Oculus. */
    private static final class ShaderBridge {
        static boolean isShaderPackInUse() {
            return net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse();
        }
    }
}
