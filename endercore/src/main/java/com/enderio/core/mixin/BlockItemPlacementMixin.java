package com.enderio.core.mixin;

import com.enderio.core.common.blockentity.EnderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockItem.class)
public abstract class BlockItemPlacementMixin {
    @Redirect(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;setPlacedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V"))
    private void endercore$applyItemState(Block block, Level level, BlockPos pos, BlockState state,
                                        LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof EnderBlockEntity entity) {
            entity.applyItemData(stack);
        }
        block.setPlacedBy(level, pos, state, placer, stack);
    }
}
