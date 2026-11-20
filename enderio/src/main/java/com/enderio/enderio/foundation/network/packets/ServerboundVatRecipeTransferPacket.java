package com.enderio.enderio.foundation.network.packets;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.enderio.api.EnderIOAPI;
import net.minecraft.resources.ResourceLocation;

public record ServerboundVatRecipeTransferPacket(int containerId, ResourceLocation recipeId, boolean maximum) {
    public static final ResourceLocation ID = EnderIOAPI.rl("vat_recipe_transfer");

    public static final PacketCodec<ServerboundVatRecipeTransferPacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.INT, ServerboundVatRecipeTransferPacket::containerId,
        PacketCodecs.RESOURCE_LOCATION, ServerboundVatRecipeTransferPacket::recipeId,
        PacketCodecs.BOOL, ServerboundVatRecipeTransferPacket::maximum,
        ServerboundVatRecipeTransferPacket::new);
}
