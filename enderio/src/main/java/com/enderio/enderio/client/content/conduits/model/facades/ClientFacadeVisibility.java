package com.enderio.enderio.client.content.conduits.model.facades;

import com.enderio.core.annotations.UseOnly;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;

// TODO: In future, support hiding specific conduit types too.
@EventBusSubscriber(modid = "enderio", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.FORGE)
public class ClientFacadeVisibility {

    private static volatile boolean FACADES_VISIBLE = true;

    @UseOnly(LogicalSide.CLIENT)
    public static boolean areFacadesVisible() {
        return FACADES_VISIBLE;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        // Update every tick on the client.

        if (Minecraft.getInstance() == null || Minecraft.getInstance().level == null) {
            return;
        }

        if (event.player != Minecraft.getInstance().player) {
            return;
        }

        setFacadesVisible(FacadeUtil.areFacadesVisible(event.player));
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ConduitBundleBlockEntity.FACADES.clear();
            ConduitBundleBlockEntity.CHUNK_FACADES.clear();
            FACADES_VISIBLE = true;
        }
    }

    private static void setFacadesVisible(boolean visible) {
        if (visible == FACADES_VISIBLE) return;
        FACADES_VISIBLE = visible;
        RenderSystem.recordRenderCall(() -> {
            var minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return;
            var chunkFacades = ConduitBundleBlockEntity.CHUNK_FACADES.get(minecraft.level.dimension());
            if (chunkFacades == null) return;

            var sections = new LongOpenHashSet(chunkFacades.keySet());
            // An ordinary glass block across a section boundary can gain or lose its shared face.
            for (var positions : chunkFacades.values()) {
                positions.forEach((long position) -> {
                    BlockPos pos = BlockPos.of(position);
                    for (Direction side : Direction.values()) {
                        sections.add(SectionPos.asLong(pos.relative(side)));
                    }
                });
            }
            sections.forEach((long section) -> minecraft.levelRenderer.setSectionDirty(
                SectionPos.x(section), SectionPos.y(section), SectionPos.z(section)));
        });
    }
}
