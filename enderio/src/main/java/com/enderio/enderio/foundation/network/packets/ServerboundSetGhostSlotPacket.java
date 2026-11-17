package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.EnderIO;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public record ServerboundSetGhostSlotPacket(int containerId, int slotIndex, ItemStack itemStack) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ServerboundSetGhostSlotPacket> TYPE = new CustomPacketPayload.Type<>(
        EnderIO.rl("set_ghost_slot"));

    public static final PacketCodec<ServerboundSetGhostSlotPacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.INT, ServerboundSetGhostSlotPacket::containerId, PacketCodecs.INT, ServerboundSetGhostSlotPacket::slotIndex,
        PacketCodecs.REQUIRED_ITEM_STACK, ServerboundSetGhostSlotPacket::itemStack, ServerboundSetGhostSlotPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
