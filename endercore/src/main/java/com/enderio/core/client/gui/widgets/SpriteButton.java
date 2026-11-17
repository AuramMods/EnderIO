package com.enderio.core.client.gui.widgets;

import com.enderio.core.client.GuiSprites;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** A button drawn entirely from a GUI atlas sprite, without a vanilla button background. */
public class SpriteButton extends Button {
    private final ResourceLocation sprite;

    public SpriteButton(int x, int y, int width, int height, ResourceLocation sprite, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        this.sprite = sprite;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.setColor(1, 1, 1, alpha);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        GuiSprites.blit(graphics, sprite, getX(), getY(), width, height);
        graphics.setColor(1, 1, 1, 1);
    }
}
