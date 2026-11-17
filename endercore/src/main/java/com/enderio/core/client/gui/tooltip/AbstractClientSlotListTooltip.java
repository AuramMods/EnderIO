package com.enderio.core.client.gui.tooltip;

import com.enderio.core.client.GuiSprites;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;


public abstract class AbstractClientSlotListTooltip implements ClientTooltipComponent {

    private static final ResourceLocation BUNDLE_TEXTURE = new ResourceLocation("textures/gui/container/bundle.png");

    private static final int SLOT_SIZE_X = 18;
    private static final int SLOT_SIZE_Y = 20;

    private final int maxSlotWidth;

    public AbstractClientSlotListTooltip() {
        this.maxSlotWidth = 5;
    }

    public AbstractClientSlotListTooltip(int maxSlotWidth) {
        this.maxSlotWidth = maxSlotWidth;
    }

    protected abstract int slotCount();

    protected abstract void renderSlotContent(int x, int y, int itemIndex, GuiGraphics guiGraphics, Font font);

    @Override
    public int getHeight() {
        return backgroundHeight() + 4;
    }

    @Override
    public int getWidth(Font font) {
        return backgroundWidth();
    }

    private int gridSizeX() {
        return Math.min(maxSlotWidth, slotCount());
    }

    private int gridSizeY() {
        return (int)Math.ceil(((double)slotCount()) / (double)this.gridSizeX());
    }

    private int backgroundWidth() {
        return this.gridSizeX() * SLOT_SIZE_X + 2;
    }

    private int backgroundHeight() {
        return this.gridSizeY() * SLOT_SIZE_Y + 2;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        if (slotCount() <= 0) {
            return;
        }
        
        int i = this.gridSizeX();
        int j = this.gridSizeY();

        int k = 0;
        for(int l = 0; l < j; ++l) {
            for(int i1 = 0; i1 < i; ++i1) {
                int j1 = x + i1 * 18 + 1;
                int k1 = y + l * 20 + 1;
                this.renderSlot(j1, k1, k++, guiGraphics, font);
            }
        }
        drawBorder(guiGraphics, x, y, i, j);
    }

    private void drawBorder(GuiGraphics graphics, int x, int y, int columns, int rows) {
        for (int column = 0; column < columns; column++) {
            blitBundle(graphics, x + 1 + column * SLOT_SIZE_X, y, 0, 20, SLOT_SIZE_X, 1);
            blitBundle(graphics, x + 1 + column * SLOT_SIZE_X, y + rows * SLOT_SIZE_Y, 0, 60, SLOT_SIZE_X, 1);
        }
        for (int row = 0; row < rows; row++) {
            blitBundle(graphics, x, y + 1 + row * SLOT_SIZE_Y, 0, 18, 1, SLOT_SIZE_Y);
            blitBundle(graphics, x + columns * SLOT_SIZE_X + 1, y + 1 + row * SLOT_SIZE_Y, 0, 18, 1, SLOT_SIZE_Y);
        }
        for (int side = 0; side < 2; side++) {
            int edgeX = x + side * (columns * SLOT_SIZE_X + 1);
            blitBundle(graphics, edgeX, y, 0, 20, 1, 1);
            blitBundle(graphics, edgeX, y + rows * SLOT_SIZE_Y, 0, 60, 1, 1);
        }
    }

    private static void blitBundle(GuiGraphics graphics, int x, int y, int u, int v, int width, int height) {
        graphics.blit(BUNDLE_TEXTURE, x, y, 0, (float) u, (float) v, width, height, 128, 128);
    }

    private void renderSlot(int x, int y, int itemIndex, GuiGraphics guiGraphics, Font font) {
        blitBundle(guiGraphics, x, y, 0, 0, SLOT_SIZE_X, SLOT_SIZE_Y);
        if (itemIndex < slotCount()) renderSlotContent(x, y, itemIndex, guiGraphics, font);
    }

    protected void blit(GuiGraphics guiGraphics, int x, int y, ResourceLocation sprite, int w, int h) {
        GuiSprites.blit(guiGraphics, sprite, x, y, w, h);
    }
}
