package com.enderio.core.network;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.menu.ClientboundSyncSlotDataPacket;
import com.enderio.core.common.network.menu.ServerboundSetSyncSlotDataPacket;
import com.enderio.core.common.network.menu.payload.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.mojang.datafixers.util.Either;

import static org.junit.jupiter.api.Assertions.*;

class PacketCodecTests {
    private record Nine(int a, int b, int c, int d, int e, int f, int g, int h, int i) {}
    private sealed interface Choice permits NumberChoice, TextChoice { int kind(); }
    private record NumberChoice(int value) implements Choice { public int kind() { return 0; } }
    private record TextChoice(String value) implements Choice { public int kind() { return 1; } }

    @Test
    void optionalEitherAndUnitPreserveTheirBranches() {
        var codec = PacketCodecs.either(PacketCodecs.INT, PacketCodecs.STRING_UTF8).optional();
        for (Optional<Either<Integer, String>> value : List.<Optional<Either<Integer, String>>>of(Optional.empty(),
                Optional.of(Either.left(-7)), Optional.of(Either.right("soul")))) {
            assertEquals(value, roundTrip(codec, value));
        }
        assertEquals("toggle", roundTrip(PacketCodec.unit("toggle"), "toggle"));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { assertThrows(EncoderException.class, () -> PacketCodec.unit("toggle").encode(buffer, "different")); }
        finally { buffer.release(); }
    }

    @Test
    void largeRecordCodecPreservesEveryFieldInWireOrder() {
        var codec = PacketCodec.composite(PacketCodecs.INT, Nine::a, PacketCodecs.INT, Nine::b,
            PacketCodecs.INT, Nine::c, PacketCodecs.INT, Nine::d, PacketCodecs.INT, Nine::e,
            PacketCodecs.INT, Nine::f, PacketCodecs.INT, Nine::g, PacketCodecs.INT, Nine::h,
            PacketCodecs.INT, Nine::i, Nine::new);
        var value = new Nine(19, -4, 312, 0, Integer.MAX_VALUE, Integer.MIN_VALUE, 7, 8, 9);
        assertEquals(value, roundTrip(codec, value));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, value);
            for (int expected : new int[] {19, -4, 312, 0, Integer.MAX_VALUE, Integer.MIN_VALUE, 7, 8, 9})
                assertEquals(expected, buffer.readInt());
        } finally { buffer.release(); }
    }

    @Test
    void dispatchSelectsConcreteCodecsAndRejectsUnknownDiscriminators() {
        var ids = PacketCodecs.idMapper(id -> id < 2 ? id : 0, Integer::intValue);
        PacketCodec<Choice> codec = ids.dispatch(Choice::kind, kind -> kind == 0
            ? PacketCodecs.INT.map(NumberChoice::new, NumberChoice::value)
            : PacketCodecs.STRING_UTF8.map(TextChoice::new, TextChoice::value));
        assertEquals(new NumberChoice(742), roundTrip(codec, new NumberChoice(742)));
        assertEquals(new TextChoice("anchor"), roundTrip(codec, new TextChoice("anchor")));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (int invalid : new int[] {-1, 2, Integer.MAX_VALUE}) {
                buffer.clear();
                buffer.writeVarInt(invalid);
                assertThrows(DecoderException.class, () -> codec.decode(buffer));
            }
            assertThrows(EncoderException.class, () -> ids.encode(buffer, 2));
        } finally { buffer.release(); }
    }
    static <T> T roundTrip(PacketCodec<T> codec, T value) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, value);
            T decoded = codec.decode(buffer);
            assertEquals(0, buffer.readableBytes(), "Decoder must consume the whole message");
            return decoded;
        } finally {
            assertTrue(buffer.release());
        }
    }

    @Test
    void primitiveAndLocationPayloadsRoundTrip() {
        List<SlotPayload> values = List.of(new NullSlotPayload(), new IntSlotPayload(Integer.MIN_VALUE),
            new LongSlotPayload(Long.MAX_VALUE), new BoolSlotPayload(true), new StringSlotPayload("Ender IO \u2603"),
            new BlockPosSlotPayload(new BlockPos(-30_000_000, -64, 29_999_999)),
            new ResourceLocationSlotPayload(new ResourceLocation("enderio", "test")));
        for (SlotPayload value : values) assertEquals(value, roundTrip(SlotPayload.STREAM_CODEC, value));
        assertEquals(1.25F, ((FloatSlotPayload) roundTrip(SlotPayload.STREAM_CODEC, new FloatSlotPayload(1.25F))).value());
    }

    @Test
    void nestedSlotPayloadsRoundTrip() {
        var value = new ListSlotPayload(List.of(new IntSlotPayload(47),
            new PairSlotPayload(new StringSlotPayload("tank"), new ListSlotPayload(List.of(new BoolSlotPayload(false))))));
        assertEquals(value, roundTrip(SlotPayload.STREAM_CODEC, value));
    }

    @Test
    void bothMenuPacketDirectionsPreserveContainerAndSlotIds() {
        var client = new ClientboundSyncSlotDataPacket(109, List.of(
            new ClientboundSyncSlotDataPacket.PayloadPair((short) 0, new IntSlotPayload(600)),
            new ClientboundSyncSlotDataPacket.PayloadPair(Short.MAX_VALUE, new NullSlotPayload())));
        assertEquals(client, roundTrip(ClientboundSyncSlotDataPacket.STREAM_CODEC, client));
        var server = new ServerboundSetSyncSlotDataPacket(109, (short) 7, new BoolSlotPayload(true));
        assertEquals(server, roundTrip(ServerboundSetSyncSlotDataPacket.STREAM_CODEC, server));
    }

    @Test
    void collectionBoundsAcceptEmptyAndExactLimit() {
        var codec = PacketCodecs.INT.list(2);
        assertEquals(List.of(), roundTrip(codec, List.of()));
        assertEquals(List.of(2, 4), roundTrip(codec, List.of(2, 4)));
    }

    @Test
    void collectionBoundsRejectOversizedEncodingAndInvalidWireCounts() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var codec = PacketCodecs.INT.list(2);
            assertThrows(EncoderException.class, () -> codec.encode(buffer, List.of(1, 2, 3)));
            for (int count : new int[] {-1, 3, Integer.MAX_VALUE}) {
                buffer.clear();
                buffer.writeVarInt(count);
                assertThrows(DecoderException.class, () -> codec.decode(buffer));
            }
        } finally {
            buffer.release();
        }
    }

    @Test
    void unknownPayloadIdsAreRejectedInsteadOfWrapping() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (int id : new int[] {-1, SlotPayloadType.values().length, Integer.MAX_VALUE}) {
                buffer.clear();
                buffer.writeVarInt(id);
                assertThrows(DecoderException.class, () -> SlotPayload.STREAM_CODEC.decode(buffer));
            }
        } finally {
            buffer.release();
        }
    }

    @Test
    void deeplyNestedPayloadsAreRejectedOnBothPaths() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SlotPayload value = new NullSlotPayload();
            for (int i = 0; i <= SlotPayload.MAX_NESTING; i++) value = new ListSlotPayload(List.of(value));
            SlotPayload tooDeep = value;
            assertThrows(EncoderException.class, () -> SlotPayload.STREAM_CODEC.encode(buffer, tooDeep));
            buffer.clear();
            for (int i = 0; i <= SlotPayload.MAX_NESTING; i++) {
                buffer.writeVarInt(SlotPayloadType.LIST.ordinal());
                buffer.writeVarInt(1);
            }
            buffer.writeVarInt(SlotPayloadType.NULL.ordinal());
            assertThrows(DecoderException.class, () -> SlotPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void truncatedPacketFailsDecoding() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeInt(9);
            assertThrows(IndexOutOfBoundsException.class, () -> ServerboundSetSyncSlotDataPacket.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void byteArrayBoundsAreConsistentInBothDirections() {
        assertArrayEquals(new byte[] {0, 1, -1}, roundTrip(PacketCodecs.BYTE_ARRAY, new byte[] {0, 1, -1}));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            assertThrows(EncoderException.class, () -> PacketCodecs.BYTE_ARRAY.encode(buffer,
                new byte[PacketCodecs.MAX_BYTE_ARRAY_SIZE + 1]));
            buffer.clear();
            buffer.writeVarInt(PacketCodecs.MAX_BYTE_ARRAY_SIZE + 1);
            assertThrows(DecoderException.class, () -> PacketCodecs.BYTE_ARRAY.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void mapRoundTripRetainsValuesAndRejectsDuplicateWireKeys() {
        var codec = PacketCodec.map(PacketCodecs.STRING_UTF8, PacketCodecs.INT);
        var expected = Map.of("input", 12, "output", 44);
        assertEquals(expected, roundTrip(codec, expected));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(2);
            buffer.writeUtf("input").writeInt(12);
            buffer.writeUtf("input").writeInt(44);
            assertThrows(DecoderException.class, () -> codec.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
