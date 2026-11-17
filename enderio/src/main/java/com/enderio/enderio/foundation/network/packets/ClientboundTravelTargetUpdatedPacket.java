package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.enderio.api.travel.TravelTarget;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record ClientboundTravelTargetUpdatedPacket(@Nullable TravelTarget target) {

    public static final ResourceLocation ID = EnderIOAPI.rl("add_travel_target");

    public static final PacketCodec<ClientboundTravelTargetUpdatedPacket> STREAM_CODEC = TravelTarget.STREAM_CODEC
        .map(ClientboundTravelTargetUpdatedPacket::new, ClientboundTravelTargetUpdatedPacket::target);

}
