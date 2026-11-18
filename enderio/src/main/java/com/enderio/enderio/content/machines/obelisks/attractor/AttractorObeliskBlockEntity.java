package com.enderio.enderio.content.machines.obelisks.attractor;

import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.core.annotations.UseOnly;
import com.enderio.core.common.capability.ServerLevelState;
import com.enderio.enderio.api.capacitor.CapacitorModifier;
import com.enderio.enderio.api.capacitor.LinearScalable;
import com.enderio.enderio.api.capacitor.QuadraticScalable;
import com.enderio.enderio.api.filter.SoulFilter;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.content.machines.obelisks.ObeliskAreaManager;
import com.enderio.enderio.content.machines.obelisks.ObeliskBlockEntity;
import com.enderio.enderio.foundation.block.entity.flags.CapacitorSupport;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.util.EntityCaptureUtils;
import com.enderio.enderio.init.EIOBlockEntities;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class AttractorObeliskBlockEntity extends ObeliskBlockEntity<AttractorObeliskBlockEntity> {

    private static final QuadraticScalable ENERGY_CAPACITY = new QuadraticScalable(CapacitorModifier.ENERGY_CAPACITY,
            MachinesConfig.COMMON.ENERGY.ATTRACTOR_CAPACITY);
    private static final LinearScalable ENERGY_USAGE = new LinearScalable(CapacitorModifier.ENERGY_USE,
            MachinesConfig.COMMON.ENERGY.ATTRACTOR_USAGE);
    private static final LinearScalable RANGE = new LinearScalable(CapacitorModifier.ENERGY_USE,
            MachinesConfig.COMMON.ATTRACTOR_RANGE);
    private static final ServerLevelState<Map<Mob, AttractorObeliskBlockEntity>> CONTROL_OWNERS =
            new ServerLevelState<>(IdentityHashMap::new);

    private final Vec3 targetPos;
    private final Map<Mob, MobControl> controlledMobs = new IdentityHashMap<>();

    @UseOnly(LogicalSide.SERVER)
    @Nullable
    private FakePlayer fakePlayer;

    public AttractorObeliskBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(EIOBlockEntities.ATTRACTOR_OBELISK.get(), worldPosition, blockState, false, CapacitorSupport.REQUIRED,
                EnergyIOMode.Input, ENERGY_CAPACITY, ENERGY_USAGE);
        targetPos = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
    }

    @UseOnly(LogicalSide.SERVER)
    private FakePlayer getFakePlayer() {
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("Level is null");
        }

        if (fakePlayer == null) {
            fakePlayer = new FakePlayer(serverLevel, new GameProfile(getMachineOwnerOrRandom(), "enderio:attractor:" + worldPosition));
            fakePlayer.setPos(targetPos.x, targetPos.y, targetPos.z);
        }

        return fakePlayer;
    }

    @Override
    protected @Nullable ObeliskAreaManager<AttractorObeliskBlockEntity> getAreaManager(ServerLevel level) {
        return null;
    }

    @Override
    public @Nullable MachineInventoryLayout createInventoryLayout() {
        return MachineInventoryLayout.builder()
                .inputSlot((integer, itemStack) -> itemStack.getCapability(EnderIOCapabilities.SOUL_FILTER).orElse(null) != null)
                .slotAccess(FILTER)
                .capacitor()
                .build();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new AttractorObeliskMenu(containerId, playerInventory, this);
    }

    @Override
    public int getMaxRange() {
        return RANGE.scaleI(this::getCapacitorData).get();
    }

    @Override
    public String getColor() {
        return MachinesConfig.CLIENT.BLOCKS.ATTRACTOR_RANGE_COLOR.get();
    }

    @Override
    public void serverTick() {
        super.serverTick();
        if (isActive() && level instanceof ServerLevel serverLevel) {
            doAttract(serverLevel);
        } else {
            releaseAll();
        }
    }

    private void doAttract(ServerLevel level) {
        AABB aabb = getAABB();
        SoulFilter filter = getSoulFilter();
        if (aabb == null || filter == null) {
            releaseAll();
            return;
        }
        boolean pullBosses = MachinesConfig.COMMON.ATTRACTOR_PULL_BOSSES.get();
        var matching = level.getEntities(EntityTypeTest.forClass(Mob.class), aabb,
                mob -> mob.isAlive() && (pullBosses || !mob.getType().is(EntityCaptureUtils.BOSSES)) && filter.test(mob));
        if (!controlledMobs.isEmpty()) {
            var inRange = Set.copyOf(matching);
            controlledMobs.entrySet().removeIf(entry -> {
                if (inRange.contains(entry.getKey())) return false;
                release(entry.getKey(), entry.getValue());
                return true;
            });
        }
        for (Mob mob : matching) {
            if (mob instanceof WitherBoss) {
                var control = claim(level, mob);
                if (control != null) {
                    control.setTarget(mob, null);
                    directPull(mob, 2.25f);
                }
            } else if (mob instanceof PathfinderMob) {
                attractMob(mob);
            } else if (mob instanceof Slime || mob instanceof Phantom) {
                var control = claim(level, mob);
                if (control != null) control.setTarget(mob, getFakePlayer());
            } else if (mob instanceof Ghast) {
                directPull(mob, 1);
            }
        }
    }

    private @Nullable MobControl claim(ServerLevel level, Mob mob) {
        var owner = CONTROL_OWNERS.get(level).putIfAbsent(mob, this);
        if (owner != null && owner != this) return null;
        return controlledMobs.computeIfAbsent(mob, MobControl::new);
    }

    private void release(Mob mob, MobControl control) {
        if (level instanceof ServerLevel serverLevel) {
            var owners = CONTROL_OWNERS.getIfPresent(serverLevel);
            if (owners != null) owners.remove(mob, this);
        }
        control.release(mob);
    }

    private void releaseAll() {
        controlledMobs.forEach(this::release);
        controlledMobs.clear();
        if (fakePlayer != null) {
            fakePlayer.discard();
            fakePlayer = null;
        }
    }

    private void attractMob(Mob mob) {
        mob.goalSelector.enableControlFlag(Goal.Flag.MOVE);
        Vec3 moveOffset = targetPos.subtract(mob.getX(), mob.getY(), mob.getZ());
        // keep them 1 block away
        moveOffset = moveOffset.subtract(moveOffset.normalize());
        mob.getNavigation().moveTo(mob.getX() + moveOffset.x, mob.getY() + moveOffset.y, mob.getZ() + moveOffset.z, 1);
    }

    private void directPull(LivingEntity mob, float speed) {
        Vec3 dir = targetPos.subtract(new Vec3(mob.xo, mob.yo, mob.zo)).normalize();
        dir = dir.scale(speed);
        AABB aabb = mob.getBoundingBox();
        aabb = aabb.move(dir);
        if (level != null && level.noCollision(mob, aabb)) {
            mob.setDeltaMovement(dir);
        }
    }

    @Override
    public void setLevel(Level level) {
        if (this.level != level) releaseAll();
        super.setLevel(level);
    }

    @Override
    public void onChunkUnloaded() {
        releaseAll();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        releaseAll();
        super.setRemoved();
    }

    private static final class MobControl {
        private final @Nullable Goal lookBlocker;
        private final @Nullable Goal targetBlocker;
        private @Nullable LivingEntity previousTarget;
        private @Nullable LivingEntity assignedTarget;
        private boolean targetChanged;

        private MobControl(Mob mob) {
            if (mob instanceof WitherBoss) {
                lookBlocker = blockGoal(mob, Goal.Flag.LOOK);
                targetBlocker = blockGoal(mob, Goal.Flag.TARGET);
            } else {
                lookBlocker = null;
                targetBlocker = null;
            }
        }

        private static Goal blockGoal(Mob mob, Goal.Flag flag) {
            // A temporary goal reserves the flag while preserving other controllers' disabled flags.
            var blocker = new Goal() {
                @Override
                public boolean canUse() { return true; }
            };
            blocker.setFlags(EnumSet.of(flag));
            mob.goalSelector.addGoal(Integer.MIN_VALUE, blocker);
            return blocker;
        }

        private void setTarget(Mob mob, @Nullable LivingEntity target) {
            var current = mob.getTarget();
            if (current == target) return;
            mob.setTarget(target);
            if (mob.getTarget() == target) {
                previousTarget = current;
                assignedTarget = target;
                targetChanged = true;
            }
        }

        private void release(Mob mob) {
            if (lookBlocker != null) mob.goalSelector.removeGoal(lookBlocker);
            if (targetBlocker != null) mob.goalSelector.removeGoal(targetBlocker);
            if (targetChanged && mob.isAlive() && mob.getTarget() == assignedTarget) {
                mob.setTarget(previousTarget != null && previousTarget.isAlive() && previousTarget.level() == mob.level()
                        ? previousTarget : null);
            }
        }
    }
}
