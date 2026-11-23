package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.fluid.CTFluidIngredient;
import com.blamejared.crafttweaker.api.fluid.IFluidStack;
import com.enderio.core.common.recipes.FluidIngredient;
import com.enderio.core.common.recipes.SizedFluidIngredient;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraftforge.fluids.FluidStack;

import java.util.stream.Stream;

final class FluidRecipeArguments {
    private FluidRecipeArguments() {}

    static FluidStack stack(IFluidStack value) {
        FluidStack stack = value.getInternal();
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Recipe fluid must be a nonempty fluid stack");
        }
        return stack.copy();
    }

    static SizedFluidIngredient ingredient(CTFluidIngredient value) {
        return value.mapTo(fluid -> {
            var stack = stack(fluid);
            if (stack.hasTag()) {
                throw new IllegalArgumentException("Ender IO fluid ingredients match fluid types and tags and do not match stack NBT");
            }
            return SizedFluidIngredient.of(stack);
        }, SizedFluidIngredient::of, FluidRecipeArguments::combine);
    }

    static <T> T option(Codec<T> codec, String value) {
        var result = codec.parse(JsonOps.INSTANCE, new JsonPrimitive(value));
        return result.result().orElseThrow(() -> new IllegalArgumentException(result.error().orElseThrow().message()));
    }

    private static SizedFluidIngredient combine(Stream<SizedFluidIngredient> values) {
        var ingredients = values.toList();
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("Fluid ingredient needs at least one alternative");
        }
        var amount = ingredients.get(0).amount();
        if (ingredients.stream().anyMatch(ingredient -> ingredient.amount() != amount)) {
            throw new IllegalArgumentException("All alternatives of an Ender IO fluid ingredient need the same amount");
        }
        return new SizedFluidIngredient(FluidIngredient.anyOf(
                ingredients.stream().map(SizedFluidIngredient::ingredient).toArray(FluidIngredient[]::new)), amount);
    }
}
