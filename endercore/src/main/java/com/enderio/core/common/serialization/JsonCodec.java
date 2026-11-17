package com.enderio.core.common.serialization;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.util.function.Function;

/** Uses existing native JSON readers/writers through DFU, retaining their validation errors. */
public final class JsonCodec {
    private JsonCodec() {}
    public static <T> Codec<T> of(Function<JsonElement, T> read, Function<T, JsonElement> write) {
        return Codec.PASSTHROUGH.flatXmap(value -> {
            try { return DataResult.success(read.apply(value.convert(JsonOps.INSTANCE).getValue())); }
            catch (RuntimeException error) { return DataResult.error(() -> "Invalid JSON data: " + error.getMessage()); }
        }, value -> {
            try { return DataResult.success(new Dynamic<>(JsonOps.INSTANCE, write.apply(value))); }
            catch (RuntimeException error) { return DataResult.error(() -> "Cannot encode JSON data: " + error.getMessage()); }
        });
    }
}
