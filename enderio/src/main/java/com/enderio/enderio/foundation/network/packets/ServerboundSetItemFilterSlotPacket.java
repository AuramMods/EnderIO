package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record ServerboundSetItemFilterSlotPacket(int containerId, int slotIndex, ItemStack itemStack) {

    public static final ResourceLocation ID = EnderIOAPI.rl("set_item_filter_slot");

    public static final PacketCodec<ServerboundSetItemFilterSlotPacket> STREAM_CODEC = PacketCodec.composite(
            PacketCodecs.INT, ServerboundSetItemFilterSlotPacket::containerId, PacketCodecs.INT, ServerboundSetItemFilterSlotPacket::slotIndex,
            PacketCodecs.REQUIRED_ITEM_STACK, ServerboundSetItemFilterSlotPacket::itemStack, ServerboundSetItemFilterSlotPacket::new);

}
