package com.enderio.core.common.network.menu.payload;

import com.enderio.core.common.network.codec.PacketCodec;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

public enum SlotPayloadType {
    // Keep these IDs stable within the port's network protocol.
    NULL(() -> NullSlotPayload.STREAM_CODEC), INT(() -> IntSlotPayload.STREAM_CODEC),
    FLOAT(() -> FloatSlotPayload.STREAM_CODEC), LONG(() -> LongSlotPayload.STREAM_CODEC),
    STRING(() -> StringSlotPayload.STREAM_CODEC), BOOL(() -> BoolSlotPayload.STREAM_CODEC),
    BLOCK_POS(() -> BlockPosSlotPayload.STREAM_CODEC), ITEM_STACK(() -> ItemStackSlotPayload.STREAM_CODEC),
    FLUID_STACK(() -> FluidStackSlotPayload.STREAM_CODEC), RESOURCE_LOCATION(() -> ResourceLocationSlotPayload.STREAM_CODEC),
    LIST(() -> ListSlotPayload.STREAM_CODEC), PAIR(() -> PairSlotPayload.STREAM_CODEC),
    COMPOUND_TAG(() -> CompoundTagSlotPayload.STREAM_CODEC);

    private static final SlotPayloadType[] VALUES = values();
    private final Supplier<PacketCodec<? extends SlotPayload>> streamCodecSupplier;

    SlotPayloadType(Supplier<PacketCodec<? extends SlotPayload>> streamCodecSupplier) {
        this.streamCodecSupplier = streamCodecSupplier;
    }

    public PacketCodec<? extends SlotPayload> streamCodec() {
        return streamCodecSupplier.get();
    }

    public static SlotPayloadType read(FriendlyByteBuf buffer) {
        int id = buffer.readVarInt();
        if (id < 0 || id >= VALUES.length) throw new DecoderException("Unknown slot payload type: " + id);
        return VALUES[id];
    }
}
