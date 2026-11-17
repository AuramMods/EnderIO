package com.enderio.core.common.recipes;

import com.enderio.core.common.network.codec.PacketCodec;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Native Forge recipe serialization with resource identity supplied by the loader/network protocol. */
public abstract class CodecRecipeSerializer<T extends Recipe<?>> implements RecipeSerializer<T> {
    public abstract MapCodec<T> codec();
    public abstract PacketCodec<T> streamCodec();
    protected abstract T withId(T recipe, ResourceLocation id);

    @Override
    public final T fromJson(ResourceLocation id, JsonObject json) {
        var result = codec().codec().parse(JsonOps.INSTANCE, json);
        return withId(result.result().orElseThrow(() -> new JsonSyntaxException(result.error().orElseThrow().message())), id);
    }
    @Override
    public final T fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) { return withId(streamCodec().decode(buffer), id); }
    @Override
    public final void toNetwork(FriendlyByteBuf buffer, T recipe) { streamCodec().encode(buffer, recipe); }
}
