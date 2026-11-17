package com.enderio.core.common.network.menu.payload;

import com.enderio.core.common.network.codec.PacketCodec;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;

public interface SlotPayload {
    int MAX_NESTING = 32;
    PacketCodec<SlotPayload> STREAM_CODEC = PacketCodec.of((buffer, value) -> write(buffer, value, 0),
        buffer -> read(buffer, 0));

    SlotPayloadType type();

    private static SlotPayload read(FriendlyByteBuf buffer, int depth) {
        if (depth > MAX_NESTING) throw new DecoderException("Slot payload nesting exceeds " + MAX_NESTING);
        SlotPayloadType type = SlotPayloadType.read(buffer);
        if (type == SlotPayloadType.LIST) {
            int size = PacketCodec.readSize(buffer, PacketCodec.MAX_COLLECTION_SIZE);
            var values = new ArrayList<SlotPayload>(Math.min(size, 256));
            for (int i = 0; i < size; i++) values.add(read(buffer, depth + 1));
            return new ListSlotPayload(values);
        }
        if (type == SlotPayloadType.PAIR) {
            return new PairSlotPayload(read(buffer, depth + 1), read(buffer, depth + 1));
        }
        return type.streamCodec().decode(buffer);
    }

    private static void write(FriendlyByteBuf buffer, SlotPayload value, int depth) {
        if (depth > MAX_NESTING) throw new EncoderException("Slot payload nesting exceeds " + MAX_NESTING);
        buffer.writeVarInt(value.type().ordinal());
        if (value instanceof ListSlotPayload list) {
            PacketCodec.writeSize(buffer, list.contents().size(), PacketCodec.MAX_COLLECTION_SIZE);
            for (SlotPayload child : list.contents()) write(buffer, child, depth + 1);
        } else if (value instanceof PairSlotPayload pair) {
            write(buffer, pair.left(), depth + 1);
            write(buffer, pair.right(), depth + 1);
        } else {
            encodeLeaf(value.type().streamCodec(), buffer, value);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends SlotPayload> void encodeLeaf(PacketCodec<T> codec, FriendlyByteBuf buffer, SlotPayload value) {
        codec.encode(buffer, (T) value);
    }
}
