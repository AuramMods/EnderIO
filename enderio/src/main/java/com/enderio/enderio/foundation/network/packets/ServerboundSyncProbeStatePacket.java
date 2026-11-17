package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.enderio.content.conduits.probe.ConduitProbeItem;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;

public record ServerboundSyncProbeStatePacket(ConduitProbeItem.State state) {

    public static final ResourceLocation ID = EnderIOAPI.rl("sync_probe_state");

    public static final PacketCodec<ServerboundSyncProbeStatePacket> STREAM_CODEC = PacketCodec.composite(
        ConduitProbeItem.State.STREAM_CODEC,
        ServerboundSyncProbeStatePacket::state,
        ServerboundSyncProbeStatePacket::new
    );

}
