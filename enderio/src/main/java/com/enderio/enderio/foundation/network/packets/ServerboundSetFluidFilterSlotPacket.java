package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.EnderIO;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraftforge.fluids.FluidStack;

public record ServerboundSetFluidFilterSlotPacket(int containerId, int slotIndex, FluidStack fluidStack)
        implements CustomPacketPayload {

    public static final Type<ServerboundSetFluidFilterSlotPacket> TYPE = new Type<>(EnderIO.rl("set_fluid_filter_slot"));

    public static final PacketCodec<ServerboundSetFluidFilterSlotPacket> STREAM_CODEC = PacketCodec.composite(
            PacketCodecs.INT, ServerboundSetFluidFilterSlotPacket::containerId, PacketCodecs.INT, ServerboundSetFluidFilterSlotPacket::slotIndex,
            PacketCodecs.FLUID_STACK, ServerboundSetFluidFilterSlotPacket::fluidStack, ServerboundSetFluidFilterSlotPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
