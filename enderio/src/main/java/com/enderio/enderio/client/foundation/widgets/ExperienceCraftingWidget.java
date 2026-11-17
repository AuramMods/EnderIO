package com.enderio.enderio.client.foundation.widgets;

import com.enderio.core.client.gui.widgets.EIOWidget;
import com.enderio.enderio.foundation.fluid.FluidStorageInfo;
import com.enderio.enderio.foundation.util.ExperienceUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

public class ExperienceCraftingWidget extends EIOWidget {
    private static final ResourceLocation GUI_ICONS = ResourceLocation.withDefaultNamespace("textures/gui/icons.png");

    private final Supplier<FluidStorageInfo> fluidStorageSupplier;
    private final Supplier<Integer> maxXP;

    public ExperienceCraftingWidget(int x, int y, int width, int height,
            Supplier<FluidStorageInfo> fluidStorageSupplier, Supplier<Integer> maxXP) {
        super(x, y, width, height);
        this.fluidStorageSupplier = fluidStorageSupplier;
        this.maxXP = maxXP;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        int k = 1;
        if (maxXP.get() > 0) {
            k = (int) (((fluidStorageSupplier.get().contents().getAmount()
                    / ((float) ExperienceUtil.getFluidFromLevel(maxXP.get()))) * this.width) - 1);
            if (k > this.width - 1) {
                k = this.width - 1;
            }
        }

        guiGraphics.blit(GUI_ICONS, this.x, this.y, this.width, this.height, 0, 64, 182, 5, 256, 256);
        if (k > 0) guiGraphics.blit(GUI_ICONS, this.x, this.y, 0, 69, k, 5);

        var font = Minecraft.getInstance().font;
        String s = "" + maxXP.get();
        guiGraphics.drawString(font, s, (this.x + this.width / 2f + 1), (float) this.y - this.height - 3, 0, false);
        guiGraphics.drawString(font, s, (this.x + this.width / 2f - 1), (float) this.y - this.height - 3, 0, false);
        guiGraphics.drawString(font, s, this.x + this.width / 2f, (float) (this.y - this.height - 3 + 1), 0, false);
        guiGraphics.drawString(font, s, this.x + this.width / 2f, (float) (this.y - this.height - 3 - 1), 0, false);
        guiGraphics.drawString(font, s, this.x + this.width / 2f, (float) this.y - this.height - 3, 8453920, false);

        if (isHovered(mouseX, mouseY)) {
            Minecraft minecraft = Minecraft.getInstance();
            guiGraphics
                    .renderTooltip(
                            minecraft.font, Component.literal(fluidStorageSupplier.get().contents().getAmount()
                                    + " mb / " + ExperienceUtil.getFluidFromLevel(maxXP.get()) + " mb"),
                            mouseX, mouseY);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }
}
