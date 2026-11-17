package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.EnderIO;
import net.minecraft.nbt.CompoundTag;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record ClientboundConduitExtraGuiDataPacket(int containerId, @Nullable CompoundTag extraGuiData)
        implements CustomPacketPayload {

    public static final Type<ClientboundConduitExtraGuiDataPacket> TYPE = new Type<>(EnderIO.rl("conduit_extra_gui_data"));

    public static final PacketCodec<ClientboundConduitExtraGuiDataPacket> STREAM_CODEC = PacketCodec.composite(
            PacketCodecs.INT, ClientboundConduitExtraGuiDataPacket::containerId,
            PacketCodecs.COMPOUND_TAG.optional().map(opt -> opt.orElse(null), Optional::ofNullable),
            ClientboundConduitExtraGuiDataPacket::extraGuiData, ClientboundConduitExtraGuiDataPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
