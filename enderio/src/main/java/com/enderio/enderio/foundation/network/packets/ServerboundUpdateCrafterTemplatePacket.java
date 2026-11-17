package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.EnderIO;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record ServerboundUpdateCrafterTemplatePacket(List<ItemStack> recipeInputs) implements CustomPacketPayload {

    public static final Type<ServerboundUpdateCrafterTemplatePacket> TYPE = new Type<>(EnderIO.rl("update_crafter_template"));

    public static final PacketCodec<ServerboundUpdateCrafterTemplatePacket> STREAM_CODEC =
        PacketCodecs.ITEM_STACK.list()
            .map(ServerboundUpdateCrafterTemplatePacket::new, ServerboundUpdateCrafterTemplatePacket::recipeInputs);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
