package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.EnderIO;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public record ServerboundSetItemFilterSlotPacket(int containerId, int slotIndex, ItemStack itemStack) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ServerboundSetItemFilterSlotPacket> TYPE = new CustomPacketPayload.Type<>(
        EnderIO.rl("set_item_filter_slot"));

    public static final PacketCodec<ServerboundSetItemFilterSlotPacket> STREAM_CODEC = PacketCodec.composite(
            PacketCodecs.INT, ServerboundSetItemFilterSlotPacket::containerId, PacketCodecs.INT, ServerboundSetItemFilterSlotPacket::slotIndex,
            PacketCodecs.REQUIRED_ITEM_STACK, ServerboundSetItemFilterSlotPacket::itemStack, ServerboundSetItemFilterSlotPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
