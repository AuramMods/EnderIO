package com.enderio.enderio.content.machines.slicer;

import net.minecraft.resources.ResourceLocation;
import com.enderio.core.common.recipes.OutputStack;
import com.enderio.enderio.foundation.MachineRecipe;
import com.enderio.enderio.foundation.util.ValidatingListCodec;
import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.core.common.recipes.RecipeRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import com.enderio.core.common.recipes.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.List;

public record SlicingRecipe(ItemStack output, List<Ingredient> inputs, int energy, ResourceLocation id)
        implements MachineRecipe<SlicingRecipe.Input> {

    public SlicingRecipe(ItemStack output, List<Ingredient> inputs, int energy) { this(output, inputs, energy, null); }

    public SlicingRecipe withId(ResourceLocation id) { return new SlicingRecipe(output, inputs, energy, java.util.Objects.requireNonNull(id)); }


    @Override
    public int getBaseEnergyCost() {
        return energy;
    }

    @Override
    public List<OutputStack> craft(Input recipeInput, RegistryAccess registryAccess) {
        return getResultStacks(registryAccess);
    }

    @Override
    public List<OutputStack> getResultStacks(RegistryAccess registryAccess) {
        return List.of(OutputStack.of(output.copy()));
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, inputs.toArray(new Ingredient[0]));
    }

    @Override
    public boolean matches(Input recipeInput, Level level) {
        for (int i = 0; i < inputs.size(); i++) {
            if (!inputs.get(i).test(recipeInput.getItem(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeRegistry.serializer(EnderIOAPI.rl("slicing"));
    }

    @Override
    public RecipeType<?> getType() {
        return RecipeRegistry.type(EnderIOAPI.rl("slicing"));
    }

    public record Input(List<ItemStack> inputs) implements RecipeInput {

        @Override
        public ItemStack getItem(int slotIndex) {
            if (slotIndex >= inputs.size()) {
                throw new IllegalArgumentException("No item for index " + slotIndex);
            }

            return inputs.get(slotIndex);
        }

        @Override
        public int size() {
            return inputs.size();
        }
    }

    public static class Serializer extends com.enderio.core.common.recipes.CodecRecipeSerializer<SlicingRecipe> {
        public static final MapCodec<SlicingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
                .group(com.enderio.core.common.recipes.RecipeStackCodecs.ITEM.fieldOf("output").forGetter(SlicingRecipe::output),
                        new ValidatingListCodec<>(com.enderio.core.common.recipes.IngredientCodecs.OPTIONAL.listOf(), 6).fieldOf("inputs")
                                .forGetter(SlicingRecipe::inputs),
                        Codec.INT.fieldOf("energy").forGetter(SlicingRecipe::energy))
                .apply(instance, SlicingRecipe::new));

        public static final PacketCodec<SlicingRecipe> STREAM_CODEC = PacketCodec.composite(
                PacketCodecs.REQUIRED_ITEM_STACK, SlicingRecipe::output,
                PacketCodecs.INGREDIENT.list(), SlicingRecipe::inputs, PacketCodecs.INT,
                SlicingRecipe::energy, SlicingRecipe::new);

        @Override
        protected SlicingRecipe withId(SlicingRecipe recipe, ResourceLocation id) { return recipe.withId(id); }

        @Override
        public MapCodec<SlicingRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<SlicingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
