package com.enderio.port.clientqa;

import com.google.gson.Gson;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Observes native spawn finalization and insertion without modifying either event. */
public final class NaturalSpawnObserver {
    private static final Gson JSON = new Gson();
    private final Set<UUID> awaitingInsertion = new HashSet<>();

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void finalized(MobSpawnEvent.FinalizeSpawn event) {
        var origin = new Vec3(event.getX(), event.getY(), event.getZ());
        var mob = event.getEntity();
        if (event.getSpawnType() != MobSpawnType.NATURAL || !inFixture(mob, origin)) return;
        awaitingInsertion.add(mob.getUUID());
        log("finalize", mob, origin, mob.position(), event.isSpawnCancelled());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void joined(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() && event.getEntity() instanceof Mob mob && awaitingInsertion.remove(mob.getUUID())) {
            log("join", mob, mob.position(), mob.position(), event.isCanceled());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void teleport(EntityTeleportEvent event) {
        if (event.getEntity() instanceof Mob mob && inFixture(mob, mob.position())) {
            log("teleport", mob, mob.position(), new Vec3(event.getTargetX(), event.getTargetY(), event.getTargetZ()), event.isCanceled());
        }
    }

    private static boolean inFixture(Mob mob, Vec3 pos) {
        if (!mob.level().dimension().equals(Level.OVERWORLD)
                || pos.y < -60 || pos.y >= -55 || pos.z < 0 || pos.z >= 16) return false;
        var type = mob.getType();
        if (type == EntityType.ZOMBIE || type == EntityType.SKELETON) {
            return pos.x >= 2096 && pos.x < 2128;
        }
        return (type == EntityType.TROPICAL_FISH || type == EntityType.AXOLOTL) && pos.x >= 5370 && pos.x < 5388;
    }

    private static void log(String observed, Mob mob, Vec3 origin, Vec3 position, boolean canceled) {
        var level = mob.level();
        var feet = BlockPos.containing(position);
        var floor = feet.below();
        boolean loaded = level.hasChunkAt(feet);
        System.out.println("PORT_SPAWN " + JSON.toJson(Map.of("event", observed, "uuid", mob.getStringUUID(),
            "type", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString(),
            "origin", coordinates(origin), "position", coordinates(position), "canceled", canceled,
            "tick", level.getGameTime(), "blockLight", loaded ? level.getBrightness(LightLayer.BLOCK, feet) : -1,
            "supported", loaded && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP),
            "inWater", loaded && level.getFluidState(feet).is(FluidTags.WATER))));
    }

    private static List<Double> coordinates(Vec3 pos) {
        return List.of(pos.x, pos.y, pos.z);
    }
}
