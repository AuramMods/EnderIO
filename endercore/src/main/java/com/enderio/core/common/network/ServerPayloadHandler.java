package com.enderio.core.common.network;

import com.enderio.core.common.blockentity.EnderBlockEntity;
import com.enderio.core.common.menu.BaseBlockEntityMenu;
import com.enderio.core.common.menu.BaseEnderMenu;
import com.enderio.core.common.network.menu.ServerboundSetSyncSlotDataPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Invoked on the server game thread; client edits must belong to the player's open menu. */
public class ServerPayloadHandler {
    private static final ServerPayloadHandler INSTANCE = new ServerPayloadHandler();

    public static ServerPayloadHandler getInstance() {
        return INSTANCE;
    }

    public void handleDataSlotChange(ClientboundDataSlotChange change, NetworkEvent.Context context) {
        var player = context.getSender();
        if (player == null || !(player.containerMenu instanceof BaseBlockEntityMenu<?> menu)
                || !menu.stillValid(player) || !menu.getBlockEntity().getBlockPos().equals(change.pos())
                || !(menu.getBlockEntity() instanceof EnderBlockEntity entity)) return;
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(change.updateData()));
        try {
            entity.serverHandleBufferChange(buffer);
            entity.setChanged();
        } finally {
            buffer.release();
        }
    }

    public void handleSetSyncSlotDataPacket(ServerboundSetSyncSlotDataPacket packet, NetworkEvent.Context context) {
        var player = context.getSender();
        if (player != null && player.containerMenu.containerId == packet.containerId()
                && player.containerMenu instanceof BaseEnderMenu menu && menu.stillValid(player)) {
            menu.serverHandleIncomingPayload(packet.index(), packet.payload());
        }
    }
}
