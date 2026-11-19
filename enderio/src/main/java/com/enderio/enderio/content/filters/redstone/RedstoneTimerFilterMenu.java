package com.enderio.enderio.content.filters.redstone;

import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.foundation.network.EIONetwork;
import com.enderio.enderio.foundation.network.packets.ServerboundTimerFilterPacket;
import com.enderio.enderio.init.EIOMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;

public class RedstoneTimerFilterMenu extends HandheldRedstoneFilterMenu {
    RedstoneTimerFilterMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(EIOMenus.REDSTONE_TIMER_FILTER.get(), containerId, inventory, hand);
    }

    public static RedstoneTimerFilterMenu factory(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        return new RedstoneTimerFilterMenu(containerId, inventory, buffer.readEnum(InteractionHand.class));
    }

    public RedstoneTimerFilter getFilter() {
        return (RedstoneTimerFilter) filterStack().getCapability(EnderIOCapabilities.REDSTONE_EXTRACT_FILTER)
            .orElseThrow(IllegalStateException::new);
    }

    public void setTimer(String timer) {
        try {
            int maxTicks = Integer.parseInt(timer);
            if (maxTicks > 0) {
                getFilter().setMaxTicks(maxTicks);
                EIONetwork.sendToServer(new ServerboundTimerFilterPacket(containerId, maxTicks));
            }
        } catch (NumberFormatException ignored) {
            // An empty field or out-of-range number leaves the current period in place.
        }
    }
}
