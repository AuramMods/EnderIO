package com.enderio.enderio.foundation.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.ForgeEventFactory;

/** Native Forge spawner ownership and initialization for a machine-managed spawn. */
public final class BlockEntitySpawner extends BaseSpawner {
    private final BlockEntity owner;

    public BlockEntitySpawner(BlockEntity owner) {
        this.owner = owner;
    }

    @Override
    public BlockEntity getSpawnerBlockEntity() {
        return owner;
    }

    @Override
    public void broadcastEvent(Level level, BlockPos pos, int eventId) {
        level.blockEvent(pos, owner.getBlockState().getBlock(), eventId, 0);
    }

    public boolean prepare(Mob mob, ServerLevel level, BlockPos pos, boolean initialize) {
        var event = ForgeEventFactory.onFinalizeSpawnSpawner(mob, level, level.getCurrentDifficultyAt(pos),
            null, null, this);
        if (mob.isSpawnCancelled()) return false;
        // Cancelling initialization is distinct from cancelling insertion. A copied
        // soul already has its state, but still participates in the Forge event.
        if (initialize && event != null) {
            mob.finalizeSpawn(level, event.getDifficulty(), event.getSpawnType(), event.getSpawnData(), event.getSpawnTag());
        }
        return !mob.isSpawnCancelled();
    }
}
