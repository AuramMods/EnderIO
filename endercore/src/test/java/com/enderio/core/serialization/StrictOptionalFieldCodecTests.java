package com.enderio.core.serialization;

import com.enderio.core.common.serialization.StrictOptionalFieldCodec;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StrictOptionalFieldCodecTests {
    private static final Codec<Optional<Integer>> CODEC = new StrictOptionalFieldCodec<>(Codec.INT, "value").codec();

    @Test
    void distinguishesMissingFieldsFromValidZeroAndPresentValues() {
        assertEquals(Optional.empty(), CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).result().orElseThrow());
        assertEquals(Optional.of(0), CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"value\":0}")).result().orElseThrow());
        assertEquals(Optional.of(41), CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"value\":41}")).result().orElseThrow());
        // DFU 8 JsonOps (the source baseline) exposes JSON null as an absent map value.
        assertEquals(Optional.empty(), CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"value\":null}")).result().orElseThrow());
    }

    @Test
    void malformedAndPartialPresentValuesAreNotConvertedToSuccessfulAbsence() {
        for (String json : new String[] {"{\"value\":\"bad\"}", "{\"value\":{}}"}) {
            var result = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
            assertTrue(result.error().isPresent());
            assertTrue(result.result().isEmpty());
        }
        Codec<Integer> partial = Codec.INT.flatXmap(value -> DataResult.error(() -> "invalid value", value), DataResult::success);
        var result = new StrictOptionalFieldCodec<>(partial, "value").codec()
            .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"value\":13}"));
        assertTrue(result.error().isPresent());
        assertTrue(result.result().isEmpty());
        assertEquals(Optional.of(13), result.resultOrPartial(message -> {}).orElseThrow());
    }

    @Test
    void roundTripsNativeNbtAndOmitsOnlyEmptyValuesWhenEncoding() {
        var tag = new CompoundTag();
        tag.putInt("value", 29);
        assertEquals(Optional.of(29), CODEC.parse(NbtOps.INSTANCE, tag).result().orElseThrow());
        assertEquals(tag, CODEC.encodeStart(NbtOps.INSTANCE, Optional.of(29)).result().orElseThrow());
        assertEquals(new CompoundTag(), CODEC.encodeStart(NbtOps.INSTANCE, Optional.empty()).result().orElseThrow());
        var prefix = new CompoundTag();
        prefix.putString("other", "preserved");
        var encoded = (CompoundTag) CODEC.encode(Optional.of(7), NbtOps.INSTANCE, prefix).result().orElseThrow();
        assertEquals("preserved", encoded.getString("other"));
        assertEquals(7, encoded.getInt("value"));
    }
}
