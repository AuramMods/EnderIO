package com.enderio.enderio.content.fire_crafting;

import com.enderio.enderio.init.EIORecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public record FireCraftingRecipe(List<Result> results, List<Block> bases, List<TagKey<Block>> baseTags,
        List<ResourceKey<Level>> dimensions, Optional<Block> blockAfterBurning) implements Recipe<RecipeInput> {

    // Get all base blocks
    public List<Block> getAllBaseBlocks() {
        List<Block> blocks = new ArrayList<>(bases);
        for (TagKey<Block> blockTagKey : baseTags) {
            BuiltInRegistries.BLOCK.getTag(blockTagKey)
                    .map(HolderSet.ListBacked::stream)
                    .orElse(Stream.empty())
                    .map(Holder::value)
                    .forEach(blocks::add);
        }
        return blocks;
    }

    public boolean isBaseValid(Block block) {
        for (TagKey<Block> tag : baseTags) {
            if (block.defaultBlockState().is(tag)) {
                return true;
            }
        }
        return bases.contains(block);
    }

    public boolean isDimensionValid(ResourceKey<Level> dimension) {
        return dimensions.contains(dimension);
    }

    @Override
    public boolean matches(RecipeInput container, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput container, HolderLookup.Provider lookupProvider) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider lookupProvider) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<FireCraftingRecipe> getSerializer() {
        return EIORecipes.FIRE_CRAFTING.serializer().get();
    }

    @Override
    public RecipeType<?> getType() {
        return EIORecipes.FIRE_CRAFTING.type().get();
    }

    public static class Serializer implements RecipeSerializer<FireCraftingRecipe> {

        public static final MapCodec<FireCraftingRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst
                .group(Codec.list(Result.CODEC).fieldOf("results").forGetter(FireCraftingRecipe::results),
                        BuiltInRegistries.BLOCK.byNameCodec()
                                .listOf()
                                .optionalFieldOf("base_blocks", List.of())
                                .forGetter(FireCraftingRecipe::bases),
                        TagKey.codec(Registries.BLOCK)
                                .listOf()
                                .optionalFieldOf("base_tags", List.of())
                                .forGetter(FireCraftingRecipe::baseTags),
                        ResourceKey.codec(Registries.DIMENSION)
                                .listOf()
                                .fieldOf("dimensions")
                                .forGetter(FireCraftingRecipe::dimensions),
                        BuiltInRegistries.BLOCK.byNameCodec()
                                .optionalFieldOf("block_after_burning")
                                .forGetter(FireCraftingRecipe::blockAfterBurning))
                .apply(inst, FireCraftingRecipe::new));

        public static final PacketCodec<FireCraftingRecipe> STREAM_CODEC = PacketCodec
                .composite(Result.STREAM_CODEC.list(), FireCraftingRecipe::results,
                        PacketCodecs.registry(net.minecraft.core.registries.BuiltInRegistries.BLOCK).list(), FireCraftingRecipe::bases,
                        PacketCodecs.RESOURCE_LOCATION.map(loc -> TagKey.create(Registries.BLOCK, loc), TagKey::location)
                                .list(),
                        FireCraftingRecipe::baseTags,
                        PacketCodecs.resourceKey(Registries.DIMENSION).list(),
                        FireCraftingRecipe::dimensions,
                        PacketCodecs.registry(net.minecraft.core.registries.BuiltInRegistries.BLOCK).optional(),
                        FireCraftingRecipe::blockAfterBurning, FireCraftingRecipe::new);

        @Override
        public MapCodec<FireCraftingRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<FireCraftingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }

    public record Result(ItemStack result, int minCount, int maxCount, float chance) {
        public static final Codec<Result> CODEC = RecordCodecBuilder
                .create(resultInstance -> resultInstance
                        .group(ItemStack.CODEC.fieldOf("result").forGetter(Result::result),
                                Codec.INT.fieldOf("min_count").forGetter(Result::minCount),
                                Codec.INT.fieldOf("max_count").forGetter(Result::maxCount),
                                Codec.FLOAT.fieldOf("chance").forGetter(Result::chance))
                        .apply(resultInstance, Result::new));

        public static final PacketCodec<Result> STREAM_CODEC = PacketCodec.composite(
                PacketCodecs.REQUIRED_ITEM_STACK, Result::result, PacketCodecs.INT, Result::minCount, PacketCodecs.INT,
                Result::maxCount, PacketCodecs.FLOAT, Result::chance, Result::new);
    }
}
