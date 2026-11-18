package com.enderio.enderio.client.content.machines.gui.screen;

import com.enderio.enderio.EnderIO;
import com.enderio.enderio.client.content.machines.gui.screen.base.MachineScreen;
import com.enderio.enderio.client.foundation.widgets.ProgressWidget;
import com.enderio.enderio.client.foundation.widgets.RedstoneControlPickerWidget;
import com.enderio.enderio.content.machines.alloy.PrimitiveAlloySmelterMenu;
import com.enderio.enderio.foundation.lang.EIOCommonLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class PrimitiveAlloySmelterScreen extends MachineScreen<PrimitiveAlloySmelterMenu> {
    private static final ResourceLocation BACKGROUND = EnderIO.rl("textures/gui/screen/primitive_alloy_smelter.png");

    public PrimitiveAlloySmelterScreen(PrimitiveAlloySmelterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableOnly(new ProgressWidget.BottomUp(BACKGROUND, menu::getBurnProgress, leftPos + 41, topPos + 37, 14, 14, 176, 0));
        addRenderableOnly(new ProgressWidget.LeftRight(BACKGROUND, menu::getCraftingProgress, leftPos + 79, topPos + 35, 24, 17, 176, 14));
        addRenderableWidget(new RedstoneControlPickerWidget(leftPos + imageWidth - 22, topPos + 6,
            menu::getRedstoneControl, menu::setRedstoneControl, EIOCommonLang.REDSTONE_MODE));

        var overlay = addIOConfigOverlay(1, leftPos + 7, topPos + 83, 162, 76);
        addIOConfigButton(leftPos + imageWidth - 22, topPos + 24, overlay);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }
}
