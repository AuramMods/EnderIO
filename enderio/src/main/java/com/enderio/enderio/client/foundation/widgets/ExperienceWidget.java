package com.enderio.enderio.client.foundation.widgets;

import com.enderio.core.client.gui.widgets.EIOWidget;
import com.enderio.enderio.foundation.util.ExperienceUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import java.util.function.Supplier;

public class ExperienceWidget extends EIOWidget {
    private static final ResourceLocation GUI_ICONS = ResourceLocation.withDefaultNamespace("textures/gui/icons.png");

    private final Supplier<FluidStack> getFluid;

    public ExperienceWidget(int x, int y, int width, int height, Supplier<FluidStack> getFluid) {
        super(x, y, width, height);
        this.getFluid = getFluid;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        ExperienceUtil.ExperienceLevel expLevel = ExperienceUtil
                .getLevelFromFluidWithLeftover(getFluid.get().getAmount());
        int fill = (int) ((((float) expLevel.experience()) / ExperienceUtil.getXpNeededForNextLevel(expLevel.level()))
                * this.width) - 1;

        guiGraphics.blit(GUI_ICONS, this.x, this.y, this.width, this.height, 0, 64, 182, 5, 256, 256);
        if (fill > 0) guiGraphics.blit(GUI_ICONS, this.x, this.y, 0, 69, fill, 5);

        var font = Minecraft.getInstance().font;
        String text = "" + expLevel.level();
        int xOffset = font.width(text) / 2;
        guiGraphics.drawString(font, text, (this.x + this.width / 2f + 1) - xOffset, (float) this.y - this.height - 3,
                0, false);
        guiGraphics.drawString(font, text, (this.x + this.width / 2f - 1) - xOffset, (float) this.y - this.height - 3,
                0, false);
        guiGraphics.drawString(font, text, this.x + this.width / 2f - xOffset, (float) (this.y - this.height - 3 + 1),
                0, false);
        guiGraphics.drawString(font, text, this.x + this.width / 2f - xOffset, (float) (this.y - this.height - 3 - 1),
                0, false);
        guiGraphics.drawString(font, text, this.x + this.width / 2f - xOffset, (float) this.y - this.height - 3,
                8453920, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }
}
