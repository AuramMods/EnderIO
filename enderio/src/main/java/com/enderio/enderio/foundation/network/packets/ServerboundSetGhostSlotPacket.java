package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record ServerboundSetGhostSlotPacket(int containerId, int slotIndex, ItemStack itemStack) {

    public static final ResourceLocation ID = EnderIOAPI.rl("set_ghost_slot");

    public static final PacketCodec<ServerboundSetGhostSlotPacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.INT, ServerboundSetGhostSlotPacket::containerId, PacketCodecs.INT, ServerboundSetGhostSlotPacket::slotIndex,
        PacketCodecs.REQUIRED_ITEM_STACK, ServerboundSetGhostSlotPacket::itemStack, ServerboundSetGhostSlotPacket::new);

}
