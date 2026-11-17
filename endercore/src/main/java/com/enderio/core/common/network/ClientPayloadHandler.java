package com.enderio.core.common.network;

import com.enderio.core.common.blockentity.EnderBlockEntity;
import com.enderio.core.common.menu.BaseEnderMenu;
import com.enderio.core.common.network.menu.ClientboundSyncSlotDataPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Invoked on the client game thread by CoreNetwork. */
public class ClientPayloadHandler {
    private static final ClientPayloadHandler INSTANCE = new ClientPayloadHandler();

    public static ClientPayloadHandler getInstance() {
        return INSTANCE;
    }

    public void handleEmitParticle(EmitParticlePacket packet, NetworkEvent.Context context) {
        clientAddParticle(packet);
    }

    public void handleEmitParticles(EmitParticlesPacket packet, NetworkEvent.Context context) {
        packet.particles().forEach(this::clientAddParticle);
    }

    private void clientAddParticle(EmitParticlePacket packet) {
        var level = Minecraft.getInstance().level;
        if (level != null) level.addParticle(packet.particleOptions(), packet.x(), packet.y(), packet.z(),
            packet.xSpeed(), packet.ySpeed(), packet.zSpeed());
    }

    public void handleDataSlotUpdate(ServerboundCDataSlotUpdate update, NetworkEvent.Context context) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getBlockEntity(update.pos()) instanceof EnderBlockEntity entity) {
            var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(update.slotData()));
            try {
                entity.clientHandleBufferSync(buffer);
            } finally {
                buffer.release();
            }
        }
    }

    public void handleSyncSlotDataPacket(ClientboundSyncSlotDataPacket packet, NetworkEvent.Context context) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu.containerId == packet.containerId()
                && player.containerMenu instanceof BaseEnderMenu menu) {
            for (var pair : packet.payloads()) menu.clientHandleIncomingPayload(pair.index(), pair.payload());
        }
    }
}
