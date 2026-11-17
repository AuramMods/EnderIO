package com.enderio.core.common.recipes;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.serialization.JsonCodec;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import java.util.Arrays;
import java.util.Objects;

/** Native Forge ingredient matching plus a positive required quantity. */
public record SizedIngredient(Ingredient ingredient, int count) {
    public static final Codec<SizedIngredient> FLAT_CODEC = JsonCodec.of(element -> {
        JsonObject json = GsonHelper.convertToJsonObject(element, "sized ingredient");
        var ingredient = json.has("ingredient") ? json.get("ingredient") : json;
        if (GsonHelper.getAsString(json, "type", "").equals("neoforge:compound")) ingredient = GsonHelper.getAsJsonArray(json, "ingredients");
        return new SizedIngredient(Ingredient.fromJson(ingredient), GsonHelper.getAsInt(json, "count", 1));
    }, value -> {
        var ingredientJson = value.ingredient.toJson();
        JsonObject json;
        if (ingredientJson.isJsonArray()) {
            json = new JsonObject();
            // Forge serializes compound ingredients as arrays, so only this form needs nesting.
            json.add("ingredient", ingredientJson);
        } else json = ingredientJson.getAsJsonObject().deepCopy();
        json.addProperty("count", value.count);
        return json;
    });
    public static final PacketCodec<SizedIngredient> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.INGREDIENT, SizedIngredient::ingredient, PacketCodecs.VAR_INT, SizedIngredient::count, SizedIngredient::new);

    public SizedIngredient {
        Objects.requireNonNull(ingredient);
        if (count <= 0 || ingredient.isEmpty()) throw new IllegalArgumentException("Sized ingredient requires a nonempty ingredient and positive count");
    }
    public static SizedIngredient of(ItemLike item, int count) { return new SizedIngredient(Ingredient.of(item), count); }
    public static SizedIngredient of(TagKey<Item> tag, int count) { return new SizedIngredient(Ingredient.of(tag), count); }
    public boolean test(ItemStack stack) { return stack.getCount() >= count && ingredient.test(stack); }
    public ItemStack[] getItems() { return Arrays.stream(ingredient.getItems()).map(stack -> stack.copyWithCount(count)).toArray(ItemStack[]::new); }
}
