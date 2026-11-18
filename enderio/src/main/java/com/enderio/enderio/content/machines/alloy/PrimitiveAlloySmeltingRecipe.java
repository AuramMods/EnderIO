package com.enderio.enderio.content.machines.alloy;

import com.enderio.enderio.init.EIORecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/** The alloy recipes used by the fuel-fired smelter in Forge 6.x worlds. */
public final class PrimitiveAlloySmeltingRecipe extends AlloySmeltingRecipe {
    private PrimitiveAlloySmeltingRecipe(AlloySmeltingRecipe recipe, ResourceLocation id) {
        super(recipe.inputs(), recipe.output(), recipe.energy(), recipe.experience(), false, id);
        if (recipe.isSmelting()) {
            throw new IllegalArgumentException("Primitive alloy recipes cannot use vanilla smelting");
        }
    }

    @Override
    public PrimitiveAlloySmeltingRecipe withId(ResourceLocation id) {
        return new PrimitiveAlloySmeltingRecipe(this, id);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return EIORecipes.PRIMITIVE_ALLOY_SMELTING.serializer().get();
    }

    @Override
    public RecipeType<?> getType() {
        return EIORecipes.PRIMITIVE_ALLOY_SMELTING.type().get();
    }

    public static final class Serializer extends AlloySmeltingRecipe.Serializer {
        @Override
        protected AlloySmeltingRecipe withId(AlloySmeltingRecipe recipe, ResourceLocation id) {
            return new PrimitiveAlloySmeltingRecipe(recipe, id);
        }
    }
}
