package com.enderio.enderio.foundation.block;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/** Native machine removal, retaining the removed entity for loot state export. */
public final class MachineWrench {
    private MachineWrench() {}

    public static InteractionResult dismantle(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || player.isSpectator()) return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        var entity = level.getBlockEntity(pos);
        var block = state.getBlock();
        block.playerWillDestroy(level, pos, state, player);
        if (!level.removeBlock(pos, false)) return InteractionResult.FAIL;
        block.destroy(level, pos, state);
        // Removal callbacks run first; capacitor-bank drops use the detached network's charge.
        for (var drop : Block.getDrops(state, level, pos, entity, player, context.getItemInHand())) {
            player.getInventory().placeItemBackInInventory(drop);
        }
        return InteractionResult.CONSUME;
    }
}
