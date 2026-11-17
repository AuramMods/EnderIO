package com.enderio.core.common.recipes;

import com.enderio.core.common.serialization.JsonCodec;
import com.enderio.core.common.serialization.StackDataCodecs;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Recipe output formats retain lowercase id/count/amount while using native typed NBT payloads. */
public final class RecipeStackCodecs {
    public static final Codec<ItemStack> ITEM = JsonCodec.of(element -> {
        var json = checked(element.getAsJsonObject());
        if (!json.has("id") && json.has("item")) json.add("id", json.remove("item"));
        if (json.has("count")) json.add("Count", json.remove("count"));
        if (!json.has("Count")) json.addProperty("Count", 1);
        if (json.has("nbt")) json.add("tag", json.remove("nbt"));
        var result = StackDataCodecs.OPTIONAL_ITEM.parse(JsonOps.INSTANCE, json);
        var stack = result.result().orElseThrow(() -> new JsonSyntaxException(result.error().orElseThrow().message()));
        if (stack.isEmpty()) throw new JsonSyntaxException("Recipe output item must be nonempty");
        return stack;
    }, stack -> {
        if (stack.isEmpty()) throw new IllegalArgumentException("Recipe output item must be nonempty");
        var json = StackDataCodecs.OPTIONAL_ITEM.encodeStart(JsonOps.INSTANCE, stack).result().orElseThrow().getAsJsonObject();
        json.add("count", json.remove("Count"));
        return json;
    });
    public static final Codec<FluidStack> FLUID = JsonCodec.of(element -> {
        var json = checked(element.getAsJsonObject());
        if (json.has("id")) json.add("FluidName", json.remove("id"));
        if (json.has("fluid")) json.add("FluidName", json.remove("fluid"));
        if (json.has("amount")) json.add("Amount", json.remove("amount"));
        var result = StackDataCodecs.OPTIONAL_FLUID.parse(JsonOps.INSTANCE, json);
        var stack = result.result().orElseThrow(() -> new JsonSyntaxException(result.error().orElseThrow().message()));
        if (stack.isEmpty()) throw new JsonSyntaxException("Recipe output fluid must be nonempty");
        return stack;
    }, stack -> {
        if (stack.isEmpty()) throw new IllegalArgumentException("Recipe output fluid must be nonempty");
        var json = StackDataCodecs.OPTIONAL_FLUID.encodeStart(JsonOps.INSTANCE, stack).result().orElseThrow().getAsJsonObject();
        json.add("id", json.remove("FluidName"));
        json.add("amount", json.remove("Amount"));
        return json;
    });
    private RecipeStackCodecs() {}
    private static JsonObject checked(JsonObject source) {
        if (source.has("components")) throw new JsonSyntaxException("Recipe data components require conversion to Forge item/fluid NBT");
        return source.deepCopy();
    }
}
