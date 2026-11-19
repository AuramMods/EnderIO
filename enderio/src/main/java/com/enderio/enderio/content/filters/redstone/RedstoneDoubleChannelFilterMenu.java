package com.enderio.enderio.content.filters.redstone;

import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.api.filter.RedstoneOutputFilter;
import com.enderio.enderio.foundation.network.EIONetwork;
import com.enderio.enderio.foundation.network.packets.ServerboundDoubleChannelPacket;
import com.enderio.enderio.init.EIOMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;

public class RedstoneDoubleChannelFilterMenu extends HandheldRedstoneFilterMenu {
    RedstoneDoubleChannelFilterMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(EIOMenus.REDSTONE_DOUBLE_CHANNEL_FILTER.get(), containerId, inventory, hand);
    }

    public static RedstoneDoubleChannelFilterMenu factory(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        return new RedstoneDoubleChannelFilterMenu(containerId, inventory, buffer.readEnum(InteractionHand.class));
    }

    public RedstoneOutputFilter getFilter() {
        return filterStack().getCapability(EnderIOCapabilities.REDSTONE_INSERT_FILTER).orElseThrow(IllegalStateException::new);
    }

    public DoubleRedstoneChannel getChannels() {
        return (DoubleRedstoneChannel) getFilter();
    }

    public void setFirstChannel(DyeColor channel) {
        setChannels(channel, getChannels().getSecondChannel());
    }

    public void setSecondChannel(DyeColor channel) {
        setChannels(getChannels().getFirstChannel(), channel);
    }

    private void setChannels(DyeColor first, DyeColor second) {
        getChannels().setChannels(first, second);
        EIONetwork.sendToServer(new ServerboundDoubleChannelPacket(containerId, first, second));
    }
}
