package com.enderio.enderio.foundation.network.packets;

import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.enderio.api.EnderIOAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;

public record ServerboundCycleIOConfigPacket(BlockPos pos, Direction side) {

    public static final ResourceLocation ID = EnderIOAPI.rl("cycle_io_config");

    public static final PacketCodec<ServerboundCycleIOConfigPacket> STREAM_CODEC = PacketCodec.composite(PacketCodecs.BLOCK_POS,
            ServerboundCycleIOConfigPacket::pos, PacketCodecs.DIRECTION, ServerboundCycleIOConfigPacket::side, ServerboundCycleIOConfigPacket::new);

}
