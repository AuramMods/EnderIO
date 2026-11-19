package com.enderio.enderio.content.filters.redstone;

import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.foundation.network.EIONetwork;
import com.enderio.enderio.foundation.network.packets.ServerboundCountFilterPacket;
import com.enderio.enderio.init.EIOMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;

public class RedstoneCountFilterMenu extends HandheldRedstoneFilterMenu {
    RedstoneCountFilterMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(EIOMenus.REDSTONE_COUNT_FILTER.get(), containerId, inventory, hand);
    }

    public static RedstoneCountFilterMenu factory(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        return new RedstoneCountFilterMenu(containerId, inventory, buffer.readEnum(InteractionHand.class));
    }

    public RedstoneCountFilter getFilter() {
        return (RedstoneCountFilter) filterStack().getCapability(EnderIOCapabilities.REDSTONE_INSERT_FILTER)
            .orElseThrow(IllegalStateException::new);
    }

    public void setCount(String maxCount) {
        try {
            int count = Integer.parseInt(maxCount);
            if (count >= 0) setConfiguration(getFilter().getChannel(), count);
        } catch (NumberFormatException ignored) {
            // An empty field or out-of-range number leaves the current count limit in place.
        }
    }

    public void setChannel(DyeColor channel) {
        setConfiguration(channel, getFilter().getMaxCount());
    }

    private void setConfiguration(DyeColor channel, int maxCount) {
        getFilter().setConfiguration(channel, maxCount);
        EIONetwork.sendToServer(new ServerboundCountFilterPacket(containerId, channel, maxCount));
    }
}
