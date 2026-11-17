package com.enderio.enderio.content.machines.painting;

import net.minecraft.resources.ResourceLocation;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.recipes.OutputStack;
import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.content.paint.BlockPaintData;
import com.enderio.enderio.foundation.MachineRecipe;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIORecipes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import com.enderio.core.common.recipes.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public record PaintingRecipe(Ingredient input, ItemStack output, ResourceLocation id) implements MachineRecipe<PaintingRecipe.Input> {

    public PaintingRecipe(Ingredient input, ItemStack output) { this(input, output, null); }

    public PaintingRecipe withId(ResourceLocation id) { return new PaintingRecipe(input, output, java.util.Objects.requireNonNull(id)); }


    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, input);
    }

    @Override
    public boolean matches(Input recipeInput, Level level) {
        return input.test(recipeInput.getItem(0)) && !recipeInput.getItem(1).isEmpty();
    }

    @Override
    public int getBaseEnergyCost() {
        return MachinesConfig.COMMON.ENERGY.PAINTING_MACHINE_ENERGY_COST.get();
    }

    @Override
    public List<OutputStack> craft(Input recipeInput, RegistryAccess registryAccess) {
        List<OutputStack> outputs = new ArrayList<>();
        ItemStack outputStack = output.copy();

        var paintItem = recipeInput.getItem(1);
        if (!(paintItem.getItem() instanceof BlockItem blockItem)) {
            throw new IllegalStateException("The item must be a block item.");
        }

        var paintBlock = blockItem.getBlock();
        EIODataComponents.BLOCK_PAINT.set(outputStack, BlockPaintData.of(paintBlock));

        outputs.add(OutputStack.of(outputStack));
        return outputs;
    }

    @Override
    public List<OutputStack> getResultStacks(RegistryAccess registryAccess) {
        return List.of(OutputStack.of(output.copy()));
    }

    @Override
    public ItemStack assemble(Input recipeInput, RegistryAccess lookupProvider) {
        return craft(recipeInput, lookupProvider).get(0).getItem();
    }

    @Override
    public ItemStack getResultItem(RegistryAccess lookupProvider) {
        return output.copy();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return EIORecipes.PAINTING.serializer().get();
    }

    @Override
    public RecipeType<?> getType() {
        return EIORecipes.PAINTING.type().get();
    }

    public record Input(ItemStack template, ItemStack paint) implements RecipeInput {

        @Override
        public ItemStack getItem(int slotIndex) {
            return switch (slotIndex) {
            case 0 -> template;
            case 1 -> paint;
            default -> throw new IllegalArgumentException("No item for index " + slotIndex);
            };
        }

        @Override
        public int size() {
            return 2;
        }
    }

    public static class Serializer extends com.enderio.core.common.recipes.CodecRecipeSerializer<PaintingRecipe> {

        public static final MapCodec<PaintingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
                .group(com.enderio.core.common.recipes.IngredientCodecs.OPTIONAL.fieldOf("input").forGetter(PaintingRecipe::input),
                        com.enderio.core.common.recipes.RecipeStackCodecs.ITEM.fieldOf("output").forGetter(PaintingRecipe::output))
                .apply(instance, PaintingRecipe::new));

        public static final PacketCodec<PaintingRecipe> STREAM_CODEC = PacketCodec.composite(
                PacketCodecs.INGREDIENT, PaintingRecipe::input, PacketCodecs.REQUIRED_ITEM_STACK, PaintingRecipe::output,
                PaintingRecipe::new);

        @Override
        protected PaintingRecipe withId(PaintingRecipe recipe, ResourceLocation id) { return recipe.withId(id); }

        @Override
        public MapCodec<PaintingRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<PaintingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
