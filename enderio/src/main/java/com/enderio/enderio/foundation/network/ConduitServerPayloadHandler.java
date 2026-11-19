package com.enderio.enderio.foundation.network;

import com.enderio.enderio.content.conduits.menu.ConduitMenu;
import com.enderio.enderio.content.conduits.probe.ConduitProbeItem;
import com.enderio.enderio.content.filters.redstone.RedstoneCountFilterMenu;
import com.enderio.enderio.content.filters.redstone.RedstoneDoubleChannelFilterMenu;
import com.enderio.enderio.content.filters.redstone.RedstoneTimerFilterMenu;
import com.enderio.enderio.foundation.network.packets.ServerboundCountFilterPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundDoubleChannelPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundOpenConduitFilterMenu;
import com.enderio.enderio.foundation.network.packets.ServerboundSyncProbeStatePacket;
import com.enderio.enderio.foundation.network.packets.ServerboundTimerFilterPacket;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;

public class ConduitServerPayloadHandler {
    private static final ConduitServerPayloadHandler INSTANCE = new ConduitServerPayloadHandler();

    public static ConduitServerPayloadHandler getInstance() {
        return INSTANCE;
    }

    public void handleDoubleChannelFilter(ServerboundDoubleChannelPacket packet, Player player) {
        if (player.containerMenu instanceof RedstoneDoubleChannelFilterMenu menu && menu.acceptsEdit(packet.containerId(), player)) {
            menu.getChannels().setChannels(packet.channel1(), packet.channel2());
        }
    }

    public void handleTimerFilter(ServerboundTimerFilterPacket packet, Player player) {
        if (packet.maxTicks() > 0 && player.containerMenu instanceof RedstoneTimerFilterMenu menu
                && menu.acceptsEdit(packet.containerId(), player)) {
            menu.getFilter().setMaxTicks(packet.maxTicks());
        }
    }

    public void handleCountFilter(ServerboundCountFilterPacket packet, Player player) {
        if (packet.maxCount() >= 0 && player.containerMenu instanceof RedstoneCountFilterMenu menu
                && menu.acceptsEdit(packet.containerId(), player)) {
            menu.getFilter().setConfiguration(packet.channel(), packet.maxCount());
        }
    }

    public void handle(ServerboundOpenConduitFilterMenu packet, Player player) {
        if (packet.containerId() == player.containerMenu.containerId) {
            // TODO: Spectator viewing filter menus is broken lol
            if (!player.isSpectator() && player.containerMenu instanceof ConduitMenu menu && menu.stillValid(player)) {
                menu.tryOpenFilterMenu(packet.slot());
            }
        }
    }

    public void handle(ServerboundSyncProbeStatePacket packet, Player player) {
        if (player.isSpectator()) return;
        ItemStack heldStack = player.getMainHandItem();

        // Sanity check before updating item
        if (heldStack.is(EIOItems.CONDUIT_PROBE.get())) {
            ConduitProbeItem.setState(player, heldStack, packet.state());
        }
    }
}
