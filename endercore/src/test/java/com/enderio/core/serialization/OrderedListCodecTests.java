package com.enderio.core.serialization;

import com.enderio.core.common.serialization.OrderedListCodec;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

// String values do not require registry lookup or a running Minecraft server.
// Keep the exact persisted index/value layout stable across the port.
public class OrderedListCodecTests {
    private static final Codec<List<String>> CODEC = OrderedListCodec.create(Codec.STRING, "");
    private static final List<String> LIST = List.of("I am first", "I am second", "I am third");
    private static final JsonElement JSON_ENCODED = JsonParser.parseString(
        "[{\"index\":0,\"value\":\"I am first\"},{\"index\":1,\"value\":\"I am second\"},{\"index\":2,\"value\":\"I am third\"}]");

    @Test
    public void orderedListCodecSerialize() {
        JsonElement encoded = CODEC.encodeStart(JsonOps.INSTANCE, LIST).result().orElseThrow();
        Assertions.assertEquals(JSON_ENCODED, encoded);
    }

    @Test
    public void orderedListCodecDeserialize() {
        List<String> decoded = CODEC.parse(JsonOps.INSTANCE, JSON_ENCODED).result().orElseThrow();
        Assertions.assertEquals(LIST, decoded);
    }

    @Test
    public void sparseOutOfOrderSlotsRetainTheirIndices() {
        var json = JsonParser.parseString("[{\"index\":2,\"value\":\"last\"},{\"index\":0,\"value\":\"first\"}]");
        Assertions.assertEquals(List.of("first", "", "last"), CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow());
    }

    @Test
    public void acceptsExactlyTheMaximumNumberOfSlots() {
        var codec = OrderedListCodec.create(3, Codec.STRING, "");
        var encoded = codec.encodeStart(JsonOps.INSTANCE, LIST).result().orElseThrow();
        Assertions.assertEquals(JSON_ENCODED, encoded);
        Assertions.assertEquals(LIST, codec.parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
    }

    @Test
    public void rejectsOversizedListsWhenEncoding() {
        var codec = OrderedListCodec.create(2, Codec.STRING, "");
        Assertions.assertTrue(codec.encodeStart(JsonOps.INSTANCE, LIST).error().isPresent());
    }

    @Test
    public void rejectsTooManySerializedEntriesEvenWhenIndicesFit() {
        var codec = OrderedListCodec.create(2, Codec.STRING, "");
        var json = JsonParser.parseString("[{\"index\":0,\"value\":\"a\"},{\"index\":0,\"value\":\"b\"},{\"index\":1,\"value\":\"c\"}]");
        Assertions.assertTrue(codec.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test
    public void rejectsNegativeAndOutOfRangeIndices() {
        var codec = OrderedListCodec.create(2, Codec.STRING, "");
        for (int index : new int[] {-1, 2}) {
            var json = JsonParser.parseString("[{\"index\":" + index + ",\"value\":\"a\"}]");
            Assertions.assertTrue(codec.parse(JsonOps.INSTANCE, json).error().isPresent());
        }
    }

    @Test
    public void zeroCapacityRoundTripsOnlyTheEmptyList() {
        var codec = OrderedListCodec.create(0, Codec.STRING, "");
        var empty = JsonParser.parseString("[]");
        Assertions.assertEquals(empty, codec.encodeStart(JsonOps.INSTANCE, List.of()).result().orElseThrow());
        Assertions.assertEquals(List.of(), codec.parse(JsonOps.INSTANCE, empty).result().orElseThrow());
        Assertions.assertTrue(codec.encodeStart(JsonOps.INSTANCE, List.of("x")).error().isPresent());
    }
}
