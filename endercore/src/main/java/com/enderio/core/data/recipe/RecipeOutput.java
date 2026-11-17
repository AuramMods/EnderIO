package com.enderio.core.data.recipe;

import com.enderio.core.common.recipes.CodecRecipeSerializer;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** Writes native finished recipes using the same codec as the runtime recipe loader. */
public final class RecipeOutput implements Consumer<FinishedRecipe> {
    private final Consumer<FinishedRecipe> output;
    private final HolderLookup.Provider registries;

    public RecipeOutput(Consumer<FinishedRecipe> output, HolderLookup.Provider registries) {
        this.output = output;
        this.registries = registries;
    }

    @Override
    public void accept(FinishedRecipe recipe) {
        output.accept(recipe);
    }

    /** Forge's native shaped/cooking serializers accept an object result with count and SNBT. */
    public Consumer<FinishedRecipe> withResult(ItemStack result) {
        return withResult(this, result);
    }

    public static Consumer<FinishedRecipe> withResult(Consumer<FinishedRecipe> output, ItemStack result) {
        if (result.isEmpty()) throw new IllegalArgumentException("Recipe output must be nonempty");
        var snapshot = result.copy();
        return recipe -> output.accept(new FinishedRecipe() {
            @Override public void serializeRecipeData(JsonObject json) {
                recipe.serializeRecipeData(json);
                var stack = new JsonObject();
                stack.addProperty("item", BuiltInRegistries.ITEM.getKey(snapshot.getItem()).toString());
                stack.addProperty("count", snapshot.getCount());
                if (snapshot.hasTag()) stack.addProperty("nbt", snapshot.getTag().toString());
                json.add("result", stack);
            }
            @Override public ResourceLocation getId() { return recipe.getId(); }
            @Override public RecipeSerializer<?> getType() { return recipe.getType(); }
            @Override public @Nullable JsonObject serializeAdvancement() { return recipe.serializeAdvancement(); }
            @Override public @Nullable ResourceLocation getAdvancementId() { return recipe.getAdvancementId(); }
        });
    }

    public void accept(ResourceLocation id, Recipe<?> recipe, @Nullable Advancement advancement, ICondition... conditions) {
        JsonObject data = encode(recipe);
        if (conditions.length != 0) {
            var encodedConditions = new JsonArray();
            for (var condition : conditions) encodedConditions.add(CraftingHelper.serialize(condition));
            data.add("conditions", encodedConditions);
        }
        output.accept(new FinishedRecipe() {
            @Override public void serializeRecipeData(JsonObject json) {
                data.entrySet().forEach(entry -> json.add(entry.getKey(), entry.getValue().deepCopy()));
            }
            @Override public ResourceLocation getId() { return id; }
            @Override public RecipeSerializer<?> getType() { return recipe.getSerializer(); }
            @Override public @Nullable JsonObject serializeAdvancement() {
                return advancement == null ? null : advancement.deconstruct().serializeToJson();
            }
            @Override public @Nullable ResourceLocation getAdvancementId() {
                return advancement == null ? null : advancement.getId();
            }
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private JsonObject encode(Recipe<?> recipe) {
        if (!(recipe.getSerializer() instanceof CodecRecipeSerializer serializer)) {
            throw new IllegalArgumentException("Recipe requires a native FinishedRecipe builder: " + recipe.getClass().getName());
        }
        var result = serializer.codec().codec().encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), recipe);
        return ((com.google.gson.JsonElement) result.getOrThrow(false,
            error -> { throw new IllegalArgumentException(String.valueOf(error)); })).getAsJsonObject();
    }
}
