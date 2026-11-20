package com.enderio.enderio.content.machines.powered_spawner;

import com.enderio.enderio.config.machines.MachinesConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.living.LivingEvent;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = "enderio", bus = EventBusSubscriber.Bus.FORGE)
public class MobSpawnTask extends PoweredSpawnerTask {

    public MobSpawnTask(PoweredSpawnerBlockEntity blockEntity) {
        super(PoweredSpawnerMode.SPAWN, blockEntity);
    }

    public MobSpawnTask(PoweredSpawnerBlockEntity blockEntity, int energyCost, EntityType<? extends Entity> entityType,
            MobSpawnMode spawnMode) {
        super(PoweredSpawnerMode.SPAWN, blockEntity, energyCost, entityType, spawnMode);
    }

    @Override
    protected void onTaskCompleted() {
        if (hasRoomForMobs()) {
            trySpawnEntity(blockEntity.getBlockPos(), (ServerLevel) blockEntity.getLevel());
        }
    }

    private boolean hasRoomForMobs() {
        AABB range = new AABB(blockEntity.getBlockPos()).inflate(blockEntity.getRange());

        List<? extends Entity> entities = blockEntity.getLevel()
                .getEntities(entityType(), range, p -> p instanceof LivingEntity);

        if (entities.size() >= MachinesConfig.COMMON.MAX_SPAWNER_ENTITIES.get()) {
            setBlockedReason(PoweredSpawnerBlockEntity.SpawnerBlockedReason.TOO_MANY_MOB);
            return false;
        }

        return true;
    }

    public void trySpawnEntity(BlockPos pos, ServerLevel level) {
        if (isCompleted()) {
            return;
        }

        AABB range = new AABB(blockEntity.getBlockPos()).inflate(blockEntity.getRange());
        long count = BlockPos.betweenClosedStream(range)
                .filter(neighbor -> level.getBlockEntity(neighbor) instanceof PoweredSpawnerBlockEntity)
                .count();
        int maximum = MachinesConfig.COMMON.MAX_SPAWNERS.get();
        float efficiency = count <= maximum ? 1f : maximum / (float) count;
        if (level.random.nextFloat() >= efficiency) {
            setBlockedReason(PoweredSpawnerBlockEntity.SpawnerBlockedReason.TOO_MANY_SPAWNER);
            return;
        }

        for (int i = 0; i < MachinesConfig.COMMON.SPAWN_AMOUNT.get(); i++) {
            RandomSource randomsource = level.getRandom();
            double x = pos.getX()
                    + (randomsource.nextDouble() - randomsource.nextDouble()) * (double) this.blockEntity.getRange()
                    + 0.5D;
            double y = pos.getY() + randomsource.nextInt(3) - 1;
            double z = pos.getZ()
                    + (randomsource.nextDouble() - randomsource.nextDouble()) * (double) this.blockEntity.getRange()
                    + 0.5D;

            if (level.noCollision(entityType().getAABB(x, y, z))) {
                Entity entity;
                switch (spawnMode()) {
                case COPY -> {
                    // TODO: Stop using BE to get entity tag data...
                    entity = EntityType.loadEntityRecursive(blockEntity.getBoundSoul().getEntityTagWithId(), level,
                            entity1 -> {
                                entity1.moveTo(x, y, z, entity1.getYRot(), entity1.getXRot());
                                entity1.setUUID(UUID.randomUUID());
                                return entity1;
                            });
                }
                case NEW -> {
                    // TODO: should we be using the ctor that accepts a position and spawn type etc.
                    entity = entityType().create(level);
                    if (entity != null) {
                        entity.moveTo(x, y, z);
                    }
                }
                default -> throw new IllegalStateException("Unexpected value: " + spawnMode());
                }

                if (entity == null) {
                    setBlockedReason(PoweredSpawnerBlockEntity.SpawnerBlockedReason.UNKNOWN_MOB);
                    break;
                }

                if (entity instanceof Mob mob) { // based on vanilla spawner
                    if (blockEntity.hasMindKiller()) {
                        mob.setNoAi(true);
                        mob.getPersistentData().putBoolean("enderio:movable", true);
                    }
                    if (!blockEntity.getForgeSpawner().prepare(mob, level, pos, spawnMode() != MobSpawnMode.COPY)) {
                        setBlockedReason(PoweredSpawnerBlockEntity.SpawnerBlockedReason.OTHER_MOD);
                        continue;
                    }
                }

                if (!level.tryAddFreshEntityWithPassengers(entity)) {
                    setBlockedReason(PoweredSpawnerBlockEntity.SpawnerBlockedReason.OTHER_MOD);
                    continue;
                }

                level.levelEvent(2004, pos, 0);
                level.gameEvent(entity, GameEvent.ENTITY_PLACE, new BlockPos((int) x, (int) y, (int) z));
                if (entity instanceof Mob mob) {
                    mob.spawnAnim();
                }

                // Successfully spawned!
                setBlockedReason(PoweredSpawnerBlockEntity.SpawnerBlockedReason.NONE);
                isComplete = true;
            }
        }
    }

    //Credit to Shadows-of-Fire for providing this workaround https://github.com/Shadows-of-Fire/Apothic-Spawners/blob/1.21/src/main/java/dev/shadowsoffire/apothic_spawners/ASEvents.java#L120
    @SubscribeEvent
    public static void tickNoAIMobs(LivingEvent.LivingTickEvent e) {
        if (e.getEntity() instanceof Mob mob) {
            if (!mob.level().isClientSide() && mob.isNoAi() && mob.getPersistentData().getBoolean("enderio:movable")) {
                mob.setNoAi(false);
                try {
                    mob.travel(new Vec3(mob.xxa, mob.yya, mob.zza));
                } finally {
                    mob.setNoAi(true);
                }
            }
        }
    }
}
