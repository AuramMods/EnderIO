package com.enderio.enderio.content.machines.vat;

import com.enderio.core.common.recipes.OutputStack;
import com.enderio.enderio.foundation.MachineRecipe;
import com.enderio.enderio.foundation.datamap.VatReagent;
import com.enderio.core.common.recipes.RecipeRegistry;
import com.enderio.enderio.api.EnderIOAPI;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import com.enderio.core.common.recipes.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import com.enderio.core.common.recipes.SizedFluidIngredient;

import java.util.List;

public record FermentingRecipe(SizedFluidIngredient input, TagKey<Item> firstReagent, TagKey<Item> secondReagent,
        FluidStack output, int ticks, ResourceLocation id) implements MachineRecipe<FermentingRecipe.Input> {

    public FermentingRecipe(SizedFluidIngredient input, TagKey<Item> firstReagent, TagKey<Item> secondReagent, FluidStack output, int ticks) { this(input, firstReagent, secondReagent, output, ticks, null); }

    public FermentingRecipe withId(ResourceLocation id) { return new FermentingRecipe(input, firstReagent, secondReagent, output, ticks, java.util.Objects.requireNonNull(id)); }


    @Override
    public int getBaseEnergyCost() {
        return 0;
    }

    @Override
    public List<OutputStack> craft(Input input, RegistryAccess registryAccess) {
        ItemStack firstInput = input.getItem(0);
        ItemStack secondInput = input.getItem(1);

        // Build modifier, ensure we use the correct item for each reagent
        double modifier;
        if (firstInput.is(firstReagent) && secondInput.is(secondReagent)) {
            modifier = getModifier(input.level(), firstInput, firstReagent);
            modifier *= getModifier(input.level(), secondInput, secondReagent);
        } else {
            modifier = getModifier(input.level(), secondInput, firstReagent);
            modifier *= getModifier(input.level(), firstInput, secondReagent);
        }

        return List.of(OutputStack.of(new FluidStack(output.getFluid(), (int) (output.getAmount() * modifier))));
    }

    @Override
    public List<OutputStack> getResultStacks(RegistryAccess registryAccess) {
        return List.of(OutputStack.of(output().copy()));
    }

    @Override
    public boolean matches(Input input, Level level) {
        FluidStack inputTank = input.inputFluid();
        if (!this.input.test(inputTank) || inputTank.getAmount() < this.input.amount()) {
            return false;
        }

        ItemStack firstInput = input.getItem(0);
        ItemStack secondInput = input.getItem(1);

        // Order independent check
        return (firstInput.is(firstReagent) && secondInput.is(secondReagent)) ||
            (firstInput.is(secondReagent) && secondInput.is(firstReagent));
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY,
            Ingredient.of(firstReagent),
            Ingredient.of(secondReagent));
    }

    public static double getModifier(Level level, ItemStack stack, TagKey<Item> reagent) {
        var map = VatReagent.DATA_MAP.get(level, stack.getItem());
        if (map != null) {
            return map.getOrDefault(reagent, 1D);
        }
        return 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeRegistry.serializer(EnderIOAPI.rl("vat_fermenting"));
    }

    @Override
    public RecipeType<?> getType() {
        return RecipeRegistry.type(EnderIOAPI.rl("vat_fermenting"));
    }

    public record Input(ItemStack firstReagent, ItemStack secondReagent, FluidStack inputFluid, Level level)
            implements RecipeInput {

        @Override
        public ItemStack getItem(int slotIndex) {
            return switch (slotIndex) {
            case 0 -> firstReagent;
            case 1 -> secondReagent;
            default -> throw new IllegalArgumentException("No item for index " + slotIndex);
            };
        }

        @Override
        public int size() {
            return 2;
        }
    }

    public static class Serializer extends com.enderio.core.common.recipes.CodecRecipeSerializer<FermentingRecipe> {
        private static final PacketCodec<TagKey<Item>> ITEM_TAG_STREAM_CODEC = PacketCodecs.RESOURCE_LOCATION
                .map(loc -> TagKey.create(Registries.ITEM, loc), TagKey::location);

        // TODO: 1.21.11: Rename left and right to first and second
        public static final MapCodec<FermentingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                SizedFluidIngredient.FLAT_CODEC.fieldOf("input").forGetter(FermentingRecipe::input),
                TagKey.codec(Registries.ITEM).fieldOf("left_reagent").forGetter(FermentingRecipe::firstReagent),
                TagKey.codec(Registries.ITEM).fieldOf("right_reagent").forGetter(FermentingRecipe::secondReagent),
                com.enderio.core.common.recipes.RecipeStackCodecs.FLUID.fieldOf("output").forGetter(FermentingRecipe::output),
                Codec.INT.fieldOf("ticks").forGetter(FermentingRecipe::ticks)).apply(instance, FermentingRecipe::new));

        public static final PacketCodec<FermentingRecipe> STREAM_CODEC = PacketCodec.composite(
                SizedFluidIngredient.STREAM_CODEC, FermentingRecipe::input, ITEM_TAG_STREAM_CODEC,
                FermentingRecipe::firstReagent, ITEM_TAG_STREAM_CODEC, FermentingRecipe::secondReagent,
                PacketCodecs.FLUID_STACK, FermentingRecipe::output, PacketCodecs.INT, FermentingRecipe::ticks,
                FermentingRecipe::new);

        @Override
        protected FermentingRecipe withId(FermentingRecipe recipe, ResourceLocation id) { return recipe.withId(id); }

        @Override
        public MapCodec<FermentingRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<FermentingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
