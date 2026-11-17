package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

public record ServerboundTransferItemsPacket(int containerId, List<Ingredient> stacks, int startslot, int endslot, boolean maxTransfer) {

    public static final ResourceLocation ID = EnderIOAPI.rl("transfer_items");

    public static final PacketCodec<ServerboundTransferItemsPacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.INT, ServerboundTransferItemsPacket::containerId,
        PacketCodecs.INGREDIENT.list(4),
        ServerboundTransferItemsPacket::stacks,
        PacketCodecs.INT,
        ServerboundTransferItemsPacket::startslot,
        PacketCodecs.INT,
        ServerboundTransferItemsPacket::endslot,
        PacketCodecs.BOOL,
        ServerboundTransferItemsPacket::maxTransfer,
        ServerboundTransferItemsPacket::new
    );

}
