package com.enderio.enderio.foundation.network;

import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.content.conduits.menu.ConduitMenu;
import com.enderio.enderio.content.conduits.probe.ConduitProbeItem;
import com.enderio.enderio.content.filters.redstone.DoubleRedstoneChannel;
import com.enderio.enderio.content.filters.redstone.RedstoneCountFilter;
import com.enderio.enderio.content.filters.redstone.RedstoneTimerFilter;
import com.enderio.enderio.foundation.network.packets.ServerboundCountFilterPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundDoubleChannelPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundOpenConduitFilterMenu;
import com.enderio.enderio.foundation.network.packets.ServerboundSyncProbeStatePacket;
import com.enderio.enderio.foundation.network.packets.ServerboundTimerFilterPacket;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraft.world.entity.player.Player;

public class ConduitServerPayloadHandler {
    private static final ConduitServerPayloadHandler INSTANCE = new ConduitServerPayloadHandler();

    public static ConduitServerPayloadHandler getInstance() {
        return INSTANCE;
    }

    public void handleDoubleChannelFilter(ServerboundDoubleChannelPacket packet, Player player) {
        if (player.isSpectator()) return;
        ItemStack mainHandItem = player.getMainHandItem();
        Object channels = mainHandItem.getCapability(EnderIOCapabilities.REDSTONE_INSERT_FILTER).orElse(null);
        if (channels == null) {
            channels = mainHandItem.getCapability(EnderIOCapabilities.REDSTONE_EXTRACT_FILTER).orElse(null);
        }

        if (channels instanceof DoubleRedstoneChannel doubleRedstoneChannel) {
            doubleRedstoneChannel.setChannels(packet.channel1(), packet.channel2());
        }
    }

    public void handleTimerFilter(ServerboundTimerFilterPacket packet, Player player) {
        if (player.isSpectator() || packet.ticks() < 0 || packet.maxTicks() <= 0) return;
        ItemStack mainHandItem = player.getMainHandItem();
        var channels = mainHandItem.getCapability(EnderIOCapabilities.REDSTONE_EXTRACT_FILTER).orElse(null);
        if (channels instanceof RedstoneTimerFilter timer) {
            timer.setTimer(packet.ticks(), packet.maxTicks());
        }
    }

    public void handleCountFilter(ServerboundCountFilterPacket packet, Player player) {
        if (player.isSpectator() || packet.count() < 0 || packet.maxCount() < 0) return;
        ItemStack mainHandItem = player.getMainHandItem();
        var channels = mainHandItem.getCapability(EnderIOCapabilities.REDSTONE_INSERT_FILTER).orElse(null);
        if (channels instanceof RedstoneCountFilter count) {
            count.setState(packet);
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
