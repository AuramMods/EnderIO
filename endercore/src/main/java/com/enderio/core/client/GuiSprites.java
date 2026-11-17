package com.enderio.core.client;

import com.enderio.core.EnderCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.TextureAtlasHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
}
