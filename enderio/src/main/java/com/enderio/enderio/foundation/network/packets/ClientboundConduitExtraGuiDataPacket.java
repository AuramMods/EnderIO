package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import net.minecraft.nbt.CompoundTag;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record ClientboundConduitExtraGuiDataPacket(int containerId, @Nullable CompoundTag extraGuiData)
        {

    public static final ResourceLocation ID = EnderIOAPI.rl("conduit_extra_gui_data");

    public static final PacketCodec<ClientboundConduitExtraGuiDataPacket> STREAM_CODEC = PacketCodec.composite(
            PacketCodecs.INT, ClientboundConduitExtraGuiDataPacket::containerId,
            PacketCodecs.COMPOUND_TAG.optional().map(opt -> opt.orElse(null), Optional::ofNullable),
            ClientboundConduitExtraGuiDataPacket::extraGuiData, ClientboundConduitExtraGuiDataPacket::new);

}
