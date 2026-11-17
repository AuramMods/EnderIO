package com.enderio.enderio.content.machines.obelisks.inhibitor;

import com.enderio.enderio.content.machines.obelisks.ObeliskAreaManager;
import com.enderio.enderio.init.EIOLevelStates;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.EntityTeleportEvent;

import java.util.Set;

@EventBusSubscriber(modid = "enderio", bus = EventBusSubscriber.Bus.FORGE)
public class InhibitorObeliskManager extends ObeliskAreaManager<InhibitorObeliskBlockEntity> {

    public static InhibitorObeliskManager getManager(ServerLevel level) {
        return EIOLevelStates.INHIBITOR_OBELISK_MANAGER.get(level);
    }

    @SuppressWarnings("unused")
    @SubscribeEvent
    public static void onTeleportEvent(EntityTeleportEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // don't work on server commands
        if (event instanceof EntityTeleportEvent.TeleportCommand
                || event instanceof EntityTeleportEvent.SpreadPlayersCommand) {
            return;
        }

        // If there is no obelisk manager, there is nothing to do.
        var obeliskManager = EIOLevelStates.INHIBITOR_OBELISK_MANAGER.getIfPresent(serverLevel);
        if (obeliskManager == null) {
            return;
        }

        var target = BlockPos.containing(event.getTargetX(), event.getTargetY(), event.getTargetZ());

        Set<InhibitorObeliskBlockEntity> obelisks = obeliskManager.getObelisksFor(target);
        if (obelisks.isEmpty()) {
            var prev = BlockPos.containing(event.getPrevX(), event.getPrevY(), event.getPrevZ());
            obelisks = obeliskManager.getObelisksFor(prev);
            if (obelisks.isEmpty()) {
                return;
            }
        }

        for (InhibitorObeliskBlockEntity obelisk : obelisks) {
            if (obelisk.handleTeleportEvent(event)) {
                break;
            }
        }
    }
}
