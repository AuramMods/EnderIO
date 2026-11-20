package com.enderio.port.clientqa;

import com.enderio.enderio.config.machines.MachinesConfig;
import com.google.gson.Gson;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;
import java.util.Map;

/** Reads tagged entities after the packaged server finishes their normal world tick. */
public final class AttractorObserver {
    private static final Gson JSON = new Gson();
    private static final BlockPos MACHINE = new BlockPos(2224, 80, 8);
    private static final AABB ARENA = new AABB(2192, 70, -24, 2260, 160, 41);

    @SubscribeEvent
    public void sample(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD) || level.getGameTime() % 3 != 0
                || !level.hasChunkAt(MACHINE)) return;
        for (var mob : level.getEntities(EntityTypeTest.forClass(Mob.class), ARENA,
                mob -> mob.isAlive() && mob.getTags().contains("port_attractor_probe"))) {
            var target = mob.getTarget();
            boolean controlled = target instanceof FakePlayer player && player.getGameProfile().getName().startsWith("enderio:attractor:");
            var path = mob.getNavigation().getPath();
            var destination = path == null || path.isDone() ? null : path.getTarget();
            System.out.println("PORT_ATTRACTOR " + JSON.toJson(Map.of(
                "type", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString(),
                "uuid", mob.getStringUUID(), "tick", level.getGameTime(), "entityTick", mob.tickCount, "position", coordinates(mob.position()),
                "motion", coordinates(mob.getDeltaMovement()), "decoy", controlled,
                "bossMode", MachinesConfig.COMMON.ATTRACTOR_PULL_BOSSES.get(),
                "navigationTarget", destination == null ? List.of() : List.of(destination.getX(), destination.getY(), destination.getZ()))));
        }
    }

    private static List<Double> coordinates(Vec3 value) {
        return List.of(value.x, value.y, value.z);
    }
}
