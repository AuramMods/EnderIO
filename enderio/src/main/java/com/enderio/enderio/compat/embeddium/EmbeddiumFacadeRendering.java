package com.enderio.enderio.compat.embeddium;

import com.enderio.enderio.client.content.conduits.ConduitFacadeRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import org.embeddedt.embeddium.api.ChunkMeshEvent;

/** Loaded only from client setup when Embeddium is installed. */
public final class EmbeddiumFacadeRendering {
    private EmbeddiumFacadeRendering() {}

    public static void init() {
        ChunkMeshEvent.BUS.addListener(event -> {
            var section = event.getSectionOrigin();
            var facades = ConduitFacadeRendering.capture(
                event.getWorld().getModelDataManager().getAt(section.chunk()), section);
            if (!facades.isEmpty()) {
                event.addMeshAppender(context -> facades.render(context.blockRenderView(), new PoseStack(),
                    context.vertexConsumerProvider()));
            }
        });
    }
}
