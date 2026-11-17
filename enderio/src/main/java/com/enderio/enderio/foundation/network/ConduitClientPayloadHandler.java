package com.enderio.enderio.foundation.network;

import com.enderio.enderio.content.conduits.menu.ConduitMenu;
import com.enderio.enderio.foundation.network.packets.ClientboundConduitExtraGuiDataPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundConduitListPacket;
import net.minecraft.world.entity.player.Player;

public class ConduitClientPayloadHandler {
    private static final ConduitClientPayloadHandler INSTANCE = new ConduitClientPayloadHandler();

    public void handle(ClientboundConduitExtraGuiDataPacket packet, Player player) {
        if (packet.containerId() == player.containerMenu.containerId) {
            if (player.containerMenu instanceof ConduitMenu menu) {
                menu.setExtraGuiData(packet.extraGuiData());
            }
        }
    }

    public void handle(ClientboundConduitListPacket packet, Player player) {
        if (packet.containerId() == player.containerMenu.containerId) {
            if (player.containerMenu instanceof ConduitMenu menu) {
                menu.setConnectedConduits(packet.conduits());
            }
        }
    }

    public static ConduitClientPayloadHandler getInstance() {
        return INSTANCE;
    }
}
