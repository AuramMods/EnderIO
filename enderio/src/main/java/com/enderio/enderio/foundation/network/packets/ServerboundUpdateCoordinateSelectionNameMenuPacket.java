package com.enderio.enderio.foundation.network.packets;

import com.enderio.core.common.network.CustomMenuPacketPayload;
import com.enderio.enderio.EnderIO;
import com.enderio.enderio.content.tools.coordinate_selector.CoordinateMenu;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ServerboundUpdateCoordinateSelectionNameMenuPacket(int containerId, String name)
    implements CustomMenuPacketPayload<CoordinateMenu> {

    public static final Type<ServerboundUpdateCoordinateSelectionNameMenuPacket> TYPE = new Type<>(EnderIO.rl("update_coordinate_selection_name"));

    public static final PacketCodec<ServerboundUpdateCoordinateSelectionNameMenuPacket> STREAM_CODEC
        = PacketCodec.composite(
            PacketCodecs.INT,
            ServerboundUpdateCoordinateSelectionNameMenuPacket::containerId,
            PacketCodecs.STRING_UTF8,
            ServerboundUpdateCoordinateSelectionNameMenuPacket::name,
            ServerboundUpdateCoordinateSelectionNameMenuPacket::new);

    @Override
    public Class<CoordinateMenu> menuClass() {
        return CoordinateMenu.class;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
