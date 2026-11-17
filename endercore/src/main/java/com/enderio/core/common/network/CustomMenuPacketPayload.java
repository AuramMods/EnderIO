package com.enderio.core.common.network;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkEvent;

public interface CustomMenuPacketPayload<Menu extends AbstractContainerMenu> {
    int containerId();

    Class<Menu> menuClass();

    default boolean isValid(NetworkEvent.Context context) {
        var player = context.getSender();
        if (player == null) return false;
        AbstractContainerMenu menu = player.containerMenu;
        return menu.containerId == containerId() && menuClass().isInstance(menu) && menu.stillValid(player);
    }

    default Menu getMenu(NetworkEvent.Context context) {
        return menuClass().cast(context.getSender().containerMenu);
    }
}
