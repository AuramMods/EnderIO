package com.enderio.core.mixin.client;

import com.enderio.core.client.gui.screen.EnderContainerScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Supplies the slot-contents hook missing from 1.20.1, preserving vanilla drag/count calculations. */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerSlotRenderingMixin {
    @Redirect(method = "renderSlot", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;renderItem(Lnet/minecraft/world/item/ItemStack;III)V"))
    private void endercore$deferItem(GuiGraphics graphics, ItemStack stack, int x, int y, int seed) {
        if (!((Object) this instanceof EnderContainerScreen<?>)) {
            graphics.renderItem(stack, x, y, seed);
        }
    }

    @Redirect(method = "renderSlot", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void endercore$renderContents(GuiGraphics graphics, Font font, ItemStack stack, int x, int y,
            @Nullable String count, GuiGraphics originalGraphics, Slot slot) {
        if ((Object) this instanceof EnderContainerScreen<?> screen) {
            screen.renderEnderSlotContents(graphics, stack, slot, count);
        } else {
            graphics.renderItemDecorations(font, stack, x, y, count);
        }
    }
}
