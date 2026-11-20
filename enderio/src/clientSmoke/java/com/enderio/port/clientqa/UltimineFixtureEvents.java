package com.enderio.port.clientqa;

import com.enderio.enderio.init.EIOBlocks;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class UltimineFixtureEvents {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void protectFixture(BlockEvent.BreakEvent event) {
        var pos = event.getPos();
        String tag = "port_protect_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
        if (event.getPlayer().getTags().contains(tag) && event.getState().is(EIOBlocks.CONDUIT_BUNDLE.get())) {
            event.setCanceled(true);
            System.out.println("PORT_ULTIMINE_CANCEL " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
        }
    }

    @SubscribeEvent
    public void keepDropsForWorldChecks(EntityItemPickupEvent event) {
        if (event.getEntity().getTags().contains("port_keep_ultimine_drops")) {
            event.setCanceled(true);
        }
    }
}
