package com.enderio.core.common.serialization;

import com.enderio.core.common.network.codec.PacketCodec;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.IIngredientSerializer;

/** Connects project codecs to Forge's actual JSON and recipe-network ingredient dispatch. */
public final class CodecIngredientSerializer<T extends Ingredient> implements IIngredientSerializer<T> {
    private final Codec<T> codec;
    private final PacketCodec<T> packetCodec;

    public CodecIngredientSerializer(Codec<T> codec, PacketCodec<T> packetCodec) {
        this.codec = codec;
        this.packetCodec = packetCodec;
    }

    @Override
    public T parse(JsonObject json) {
        var result = codec.parse(JsonOps.INSTANCE, json);
        return result.result().orElseThrow(() -> new JsonSyntaxException(result.error().orElseThrow().message()));
    }

    @Override
    public T parse(FriendlyByteBuf buffer) { return packetCodec.decode(buffer); }

    @Override
    public void write(FriendlyByteBuf buffer, T ingredient) { packetCodec.encode(buffer, ingredient); }

    public JsonObject toJson(T ingredient) {
        var result = codec.encodeStart(JsonOps.INSTANCE, ingredient);
        var json = result.result().orElseThrow(() -> new IllegalArgumentException(result.error().orElseThrow().message()))
            .getAsJsonObject();
        json.addProperty("type", CraftingHelper.getID(this).toString());
        return json;
    }
}
