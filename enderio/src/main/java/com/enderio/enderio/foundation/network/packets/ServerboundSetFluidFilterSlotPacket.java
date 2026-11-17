package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

public record ServerboundSetFluidFilterSlotPacket(int containerId, int slotIndex, FluidStack fluidStack)
        {

    public static final ResourceLocation ID = EnderIOAPI.rl("set_fluid_filter_slot");

    public static final PacketCodec<ServerboundSetFluidFilterSlotPacket> STREAM_CODEC = PacketCodec.composite(
            PacketCodecs.INT, ServerboundSetFluidFilterSlotPacket::containerId, PacketCodecs.INT, ServerboundSetFluidFilterSlotPacket::slotIndex,
            PacketCodecs.FLUID_STACK, ServerboundSetFluidFilterSlotPacket::fluidStack, ServerboundSetFluidFilterSlotPacket::new);

}
