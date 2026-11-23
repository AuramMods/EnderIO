package com.enderio.enderio.foundation.network.packets;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.enderio.api.EnderIOAPI;
import net.minecraft.resources.ResourceLocation;

public record ServerboundAlloyRecipeTransferPacket(int containerId, ResourceLocation recipeId, boolean maximum) {
    public static final ResourceLocation ID = EnderIOAPI.rl("alloy_recipe_transfer");

    public static final PacketCodec<ServerboundAlloyRecipeTransferPacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.INT, ServerboundAlloyRecipeTransferPacket::containerId,
        PacketCodecs.RESOURCE_LOCATION, ServerboundAlloyRecipeTransferPacket::recipeId,
        PacketCodecs.BOOL, ServerboundAlloyRecipeTransferPacket::maximum,
        ServerboundAlloyRecipeTransferPacket::new);
}
