package com.enderio.core.client.gui.widgets;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

// The same as AbstractButton but does not show the white outline when focussed.
public abstract class EnderButton extends AbstractWidget {
    public EnderButton(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    public abstract void onPress();

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, this.alpha);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        int textureY = 46 + (!active ? 0 : isHovered() ? 40 : 20);
        guiGraphics.blitNineSliced(WIDGETS_LOCATION, getX(), getY(), getWidth(), getHeight(),
            20, 4, 200, 20, 0, textureY);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        renderButtonFace(guiGraphics, mouseX, mouseY, partialTick);
    }

    public abstract void renderButtonFace(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks);

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.onPress();
    }
}
