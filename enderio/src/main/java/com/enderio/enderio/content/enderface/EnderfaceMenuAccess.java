package com.enderio.enderio.content.enderface;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Called from the Forge-added Container helper by the Enderface coremod. */
public final class EnderfaceMenuAccess {
    private EnderfaceMenuAccess() {}

    public static boolean stillValid(boolean original, BlockEntity entity, Player player) {
        if (original) return true;
        var level = entity.getLevel();
        return level != null && level.getBlockEntity(entity.getBlockPos()) == entity
            && EnderfaceBlockEntity.canPlayerInteractWithBlock(player, level, entity.getBlockPos());
    }
}
