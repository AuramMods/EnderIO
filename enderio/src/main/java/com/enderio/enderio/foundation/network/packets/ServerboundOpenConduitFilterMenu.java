package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.EnderIO;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ServerboundOpenConduitFilterMenu(int containerId, int slot) implements CustomPacketPayload {

    public static final Type<ServerboundOpenConduitFilterMenu> TYPE = new Type<>(EnderIO.rl("client_open_conduit_filter_menu"));

    public static final PacketCodec<ServerboundOpenConduitFilterMenu> STREAM_CODEC = PacketCodec.composite(
            PacketCodecs.INT, ServerboundOpenConduitFilterMenu::containerId, PacketCodecs.INT, ServerboundOpenConduitFilterMenu::slot,
            ServerboundOpenConduitFilterMenu::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
