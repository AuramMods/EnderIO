package com.enderio.port.clientqa;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Rewrites spawn relocation only when the dedicated fixture explicitly enables the dry target. */
public final class StriderTeleportRedirect {
    private static final BlockPos CONTROL = new BlockPos(5408, 2, 0);

    @SubscribeEvent
    public void redirect(EntityTeleportEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !NaturalSpawnObserver.inStriderFixture(mob, mob.position())) return;
        if (!mob.level().getBlockState(CONTROL).is(Blocks.GOLD_BLOCK)) return;
        var target = BlockPos.containing(event.getTargetX(), event.getTargetY(), event.getTargetZ());
        if (!mob.level().getFluidState(target).is(FluidTags.LAVA)) return;
        event.setTargetX(5410.5);
        event.setTargetY(5);
        event.setTargetZ(8.5);
    }
}
