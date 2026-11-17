package com.enderio.core.common.network.menu.payload;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import net.minecraftforge.fluids.FluidStack;

public record FluidStackSlotPayload(FluidStack value) implements SlotPayload {

    public static final PacketCodec<FluidStackSlotPayload> STREAM_CODEC = PacketCodecs.FLUID_STACK
            .map(FluidStackSlotPayload::new, FluidStackSlotPayload::value);

    @Override
    public SlotPayloadType type() {
        return SlotPayloadType.FLUID_STACK;
    }
}
