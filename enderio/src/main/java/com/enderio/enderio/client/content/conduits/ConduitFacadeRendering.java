package com.enderio.enderio.client.content.conduits;

import com.enderio.enderio.client.content.conduits.model.bundle.ConduitBundleRenderState;
import com.enderio.enderio.client.content.conduits.model.facades.ClientFacadeVisibility;
import com.enderio.enderio.client.foundation.renderer.FixedAlphaVertexConsumer;
import com.enderio.enderio.compat.ModCompatHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraftforge.client.model.data.ModelData;

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
                        dispatcher.getModelRenderer().tesselateBlock(region, model, state, pos, poseStack, consumer,
                            true, random, 42L, OverlayTexture.NO_OVERLAY, data, layer);
                    }
                } finally {
                    poseStack.popPose();
                }
            }
        }
    }

    /** Keep optional shader API linkage outside the path used without Oculus. */
    private static final class ShaderBridge {
        static boolean isShaderPackInUse() {
            return net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse();
        }
    }
}
