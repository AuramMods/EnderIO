package com.enderio.core.common.recipes;

import com.enderio.core.common.serialization.JsonCodec;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.world.item.crafting.Ingredient;

public final class IngredientCodecs {
    public static final Codec<Ingredient> OPTIONAL = JsonCodec.of(Ingredient::fromJson, Ingredient::toJson);
    public static final Codec<Ingredient> REQUIRED = JsonCodec.of(json -> {
        Ingredient ingredient = Ingredient.fromJson(json);
        if (ingredient.isEmpty()) throw new JsonSyntaxException("Recipe ingredient cannot be empty");
        return ingredient;
    }, ingredient -> {
        if (ingredient.isEmpty()) throw new IllegalArgumentException("Recipe ingredient cannot be empty");
        return ingredient.toJson();
    });
    private IngredientCodecs() {}
}
