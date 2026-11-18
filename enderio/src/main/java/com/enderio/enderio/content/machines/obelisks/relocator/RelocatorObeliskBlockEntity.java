package com.enderio.enderio.content.machines.obelisks.relocator;

import com.enderio.enderio.api.EnderIOCapabilities;
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
import com.enderio.enderio.init.EIOBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn;
import org.jetbrains.annotations.Nullable;

public class RelocatorObeliskBlockEntity extends ObeliskBlockEntity<RelocatorObeliskBlockEntity> {

    private static final QuadraticScalable ENERGY_CAPACITY = new QuadraticScalable(CapacitorModifier.ENERGY_CAPACITY,
            MachinesConfig.COMMON.ENERGY.RELOCATOR_CAPACITY);
    private static final LinearScalable ENERGY_USAGE = new LinearScalable(CapacitorModifier.ENERGY_USE,
            MachinesConfig.COMMON.ENERGY.RELOCATOR_USAGE);
    private static final LinearScalable RANGE = new LinearScalable(CapacitorModifier.ENERGY_USE,
            MachinesConfig.COMMON.RELOCATOR_RANGE);

    public RelocatorObeliskBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(EIOBlockEntities.RELOCATOR_OBELISK.get(), worldPosition, blockState, false, CapacitorSupport.REQUIRED,
                EnergyIOMode.Input, ENERGY_CAPACITY, ENERGY_USAGE);
    }

    @Override
    protected @Nullable ObeliskAreaManager<RelocatorObeliskBlockEntity> getAreaManager(ServerLevel level) {
        return RelocatorObeliskManager.getManager(level);
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
        return new RelocatorObeliskMenu(containerId, playerInventory, this);
    }

    @Override
    public int getMaxRange() {
        return RANGE.scaleI(this::getCapacitorData).get();
    }

    @Override
    public String getColor() {
        return MachinesConfig.CLIENT.BLOCKS.RELOCATOR_RANGE_COLOR.get();
    }

    public boolean handleSpawnEvent(FinalizeSpawn event) {
        if (!(level instanceof ServerLevel serverLevel) || !isActive() || getAABB() == null
                || !getAABB().contains(event.getX(), event.getY(), event.getZ())) {
            return false;
        }
        SoulFilter filter = getSoulFilter();
        Mob mob = event.getEntity();
        if (filter == null || !filter.test(mob)) {
            return false;
        }

        var random = serverLevel.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            double x = worldPosition.getX() + (random.nextDouble() - random.nextDouble()) * 5 + 0.5;
            double z = worldPosition.getZ() + (random.nextDouble() - random.nextDouble()) * 5 + 0.5;
            int firstHeight = random.nextInt(3);
            for (int height = 0; height < 3; height++) {
                double y = worldPosition.getY() + (firstHeight + height) % 3 - 1;
                if (!canReceiveMob(serverLevel, mob, x, y, z)) continue;
                var teleport = new EntityTeleportEvent(mob, x, y, z);
                if (MinecraftForge.EVENT_BUS.post(teleport)) return false;
                double targetX = teleport.getTargetX();
                double targetY = teleport.getTargetY();
                double targetZ = teleport.getTargetZ();
                if (!canReceiveMob(serverLevel, mob, targetX, targetY, targetZ)) return false;
                mob.moveTo(targetX, targetY, targetZ, mob.getYRot(), mob.getXRot());
                return true;
            }
        }
        return false;
    }

    private static boolean canReceiveMob(ServerLevel level, Mob mob, double x, double y, double z) {
        var pos = BlockPos.containing(x, y, z);
        var type = mob.getType();
        return level.hasChunkAt(pos)
                && NaturalSpawner.isSpawnPositionOk(SpawnPlacements.getPlacementType(type), level, pos, type)
                && level.noCollision(mob, mob.getBoundingBox().move(x - mob.getX(), y - mob.getY(), z - mob.getZ()));
    }
}
