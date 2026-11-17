package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record ServerboundUpdateCrafterTemplatePacket(int containerId, List<ItemStack> recipeInputs) {

    public static final ResourceLocation ID = EnderIOAPI.rl("update_crafter_template");

    public static final PacketCodec<ServerboundUpdateCrafterTemplatePacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.INT, ServerboundUpdateCrafterTemplatePacket::containerId,
        PacketCodecs.ITEM_STACK.list(9), ServerboundUpdateCrafterTemplatePacket::recipeInputs,
        ServerboundUpdateCrafterTemplatePacket::new);
}
