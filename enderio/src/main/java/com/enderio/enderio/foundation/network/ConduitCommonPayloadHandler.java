package com.enderio.enderio.foundation.network;

import com.enderio.enderio.content.conduits.menu.ConduitMenu;
import com.enderio.enderio.foundation.network.packets.SetConduitConnectionConfigPacket;
import net.minecraft.world.entity.player.Player;

public class ConduitCommonPayloadHandler {
    private static final ConduitCommonPayloadHandler INSTANCE = new ConduitCommonPayloadHandler();

    public void handle(SetConduitConnectionConfigPacket packet, Player player) {
        if (packet.containerId() == player.containerMenu.containerId) {
            if (!player.isSpectator() && player.containerMenu instanceof ConduitMenu menu && menu.stillValid(player)) {
                menu.handleConnectionConfigUpdate(packet);
            }
        }
    }

    public static ConduitCommonPayloadHandler getInstance() {
        return INSTANCE;
    }
}
