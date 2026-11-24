package com.enderio.enderio.client.content.machines.gui.screen;

import com.enderio.core.client.gui.screen.EnderContainerScreen;
import com.enderio.enderio.EnderIO;
import com.enderio.enderio.client.content.machines.gui.widget.EnergyWidget;
import com.enderio.enderio.client.foundation.widgets.RedstoneControlPickerWidget;
import com.enderio.enderio.client.foundation.widgets.ioconfig.IOConfigButton;
import com.enderio.enderio.client.foundation.widgets.ioconfig.IOConfigOverlay;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorBankManager;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorBankMenu;
import com.enderio.enderio.foundation.lang.EIOCommonLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

public class CapacitorBankScreen extends EnderContainerScreen<CapacitorBankMenu> {

    public static final ResourceLocation BG_TEXTURE = EnderIO.rl("textures/gui/screen/capacitor_bank.png");
    private IOConfigOverlay ioConfigOverlay;

    public CapacitorBankScreen(CapacitorBankMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        addRenderableOnly(new EnergyWidget(8 + leftPos, 9 + topPos, 9, 68, menu::getEnergyStorage));

        addRenderableWidget(new RedstoneControlPickerWidget(leftPos + imageWidth - 6 - 16, topPos + 6,
            menu::getRedstoneControl, menu::setRedstoneControl, EIOCommonLang.REDSTONE_MODE));

        ioConfigOverlay = addIOConfigOverlay(1, leftPos + 7, topPos + 83, 162, 76);
        addIOConfigButton(leftPos + imageWidth - 6 - 16, topPos + 24, ioConfigOverlay);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(BG_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ioConfigOverlay.setConfigurableBlocks(bankBlocks());
    }

    private List<BlockPos> bankBlocks() {
        var bank = getMenu().getBlockEntity();
        var data = CapacitorBankManager.getData(bank.getUuid());
        return data == null || data.nodes().isEmpty() ? List.of(bank.getBlockPos()) : data.nodes();
    }

    protected IOConfigOverlay addIOConfigOverlay(int layer, int x, int y, int width, int height) {
        var widget = addOverlayRenderable(layer, new IOConfigOverlay(x, y, width, height, bankBlocks()));
        addRestorableState("io_config", widget);
        widget.setVisible(false);
        return widget;
    }

    protected IOConfigButton addIOConfigButton(int x, int y, IOConfigOverlay configRenderer) {
        return addRenderableWidget(new IOConfigButton(x, y, configRenderer));
    }
}
