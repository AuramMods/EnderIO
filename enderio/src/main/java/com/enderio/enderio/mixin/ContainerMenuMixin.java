package com.enderio.enderio.mixin;

import com.enderio.enderio.content.enderface.EnderfaceBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMenuMixin {
    @Inject(method = "stillValid", at = @At("RETURN"), cancellable = true)
    private static void enderio$allowEnderface(ContainerLevelAccess access, Player player, Block block,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && access.evaluate((level, pos) -> level.getBlockState(pos).is(block)
            && EnderfaceBlockEntity.canPlayerInteractWithBlock(player, level, pos), false)) {
            cir.setReturnValue(true);
        }
    }
}
