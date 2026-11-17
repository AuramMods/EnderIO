package com.enderio.core.client;

import com.enderio.core.EnderCore;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.TextureAtlasHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Reloadable atlas for the mod's existing textures/gui/sprites assets on 1.20.1. */
@Mod.EventBusSubscriber(modid = EnderCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GuiSprites extends TextureAtlasHolder {
    private static GuiSprites instance;

    private GuiSprites(TextureManager textures) {
        super(textures, EnderCore.loc("textures/atlas/gui.png"), EnderCore.loc("gui"));
    }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        instance = new GuiSprites(Minecraft.getInstance().getTextureManager());
        event.registerReloadListener(instance);
    }

    public static void blit(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int width, int height) {
        graphics.blit(x, y, 0, width, height, instance.getSprite(sprite));
    }

    /** Draw a cropped region in the logical dimensions of the complete sprite. */
    public static void blit(GuiGraphics graphics, ResourceLocation sprite, int spriteWidth, int spriteHeight,
            int u, int v, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        TextureAtlasSprite texture = instance.getSprite(sprite);
        RenderSystem.setShaderTexture(0, texture.atlasLocation());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        writeRegion(builder, graphics.pose().last().pose(), texture, spriteWidth, spriteHeight, u, v, x, y, width, height);
        BufferUploader.drawWithShader(builder.end());
    }

    /** CPU vertex generation shared by drawing and regression tests. */
    static void writeRegion(VertexConsumer consumer, Matrix4f pose, TextureAtlasSprite sprite, int spriteWidth,
            int spriteHeight, int u, int v, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        float minU = sprite.getU(16.0 * u / spriteWidth);
        float maxU = sprite.getU(16.0 * (u + width) / spriteWidth);
        float minV = sprite.getV(16.0 * v / spriteHeight);
        float maxV = sprite.getV(16.0 * (v + height) / spriteHeight);
        consumer.vertex(pose, x, y, 0).uv(minU, minV).endVertex();
        consumer.vertex(pose, x, y + height, 0).uv(minU, maxV).endVertex();
        consumer.vertex(pose, x + width, y + height, 0).uv(maxU, maxV).endVertex();
        consumer.vertex(pose, x + width, y, 0).uv(maxU, minV).endVertex();
    }
}
