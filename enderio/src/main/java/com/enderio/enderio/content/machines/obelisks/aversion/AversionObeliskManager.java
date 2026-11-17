package com.enderio.enderio.content.machines.obelisks.aversion;

import com.enderio.enderio.content.machines.obelisks.ObeliskAreaManager;
import com.enderio.enderio.init.EIOLevelStates;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn;

import java.util.Set;

@EventBusSubscriber(modid = "enderio", bus = EventBusSubscriber.Bus.FORGE)
public class AversionObeliskManager extends ObeliskAreaManager<AversionObeliskBlockEntity> {

    public static AversionObeliskManager getManager(ServerLevel serverLevel) {
        return EIOLevelStates.AVERSION_OBELISK_MANAGER.get(serverLevel);
    }

    @SuppressWarnings("unused")
    @SubscribeEvent
    public static void onSpawnEvent(FinalizeSpawn event) {
        // Only affects natural spawns
        if (event.getSpawnType() != MobSpawnType.NATURAL) {
            return;
        }

        // If there is no obelisk manager, there is nothing to do.
        ServerLevelAccessor levelAccessor = event.getLevel();
        ServerLevel level = levelAccessor.getLevel();
        var obeliskManager = EIOLevelStates.AVERSION_OBELISK_MANAGER.getIfPresent(level);
        if (obeliskManager == null) {
            return;
        }

        var pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());

        Set<AversionObeliskBlockEntity> obelisks = obeliskManager.getObelisksFor(pos);
        if (obelisks.isEmpty()) {
            return;
        }

        for (AversionObeliskBlockEntity obelisk : obelisks) {
            if (obelisk.handleSpawnEvent(event)) {
                break;
            }
        }
    }
}
