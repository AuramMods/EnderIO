package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.EnderIO;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

public record ServerboundTransferItemsPacket(List<Ingredient> stacks, int startslot, int endslot, boolean maxTransfer) implements CustomPacketPayload {

    public static final Type<ServerboundTransferItemsPacket> TYPE = new Type<>(EnderIO.rl("transfer_items"));

    public static final PacketCodec<ServerboundTransferItemsPacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.INGREDIENT.list(),
        ServerboundTransferItemsPacket::stacks,
        PacketCodecs.INT,
        ServerboundTransferItemsPacket::startslot,
        PacketCodecs.INT,
        ServerboundTransferItemsPacket::endslot,
        PacketCodecs.BOOL,
        ServerboundTransferItemsPacket::maxTransfer,
        ServerboundTransferItemsPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
