package com.enderio.enderio.content.machines.sag_mill;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.recipes.CodecRecipeSerializer;
import com.enderio.enderio.api.components.GrindingBallData;
import com.enderio.enderio.init.EIORecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

/** The datapack definition referenced by Forge 6 SAG Mill saves. */
public record Forge6GrindingBallRecipe(@Nullable ResourceLocation id, ResourceLocation item,
                                       GrindingBallData modifiers) implements Recipe<Container> {
    private static final MapCodec<GrindingBallData> LEGACY_MODIFIERS = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            Codec.FLOAT.fieldOf("grinding").forGetter(GrindingBallData::outputMultiplier),
            Codec.FLOAT.fieldOf("chance").forGetter(GrindingBallData::bonusMultiplier),
            Codec.FLOAT.fieldOf("power").forGetter(GrindingBallData::powerUse),
            Codec.INT.fieldOf("durability").forGetter(GrindingBallData::durability)
        ).apply(instance, GrindingBallData::new));

    private static final MapCodec<Forge6GrindingBallRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            ResourceLocation.CODEC.fieldOf("item").forGetter(Forge6GrindingBallRecipe::item),
            LEGACY_MODIFIERS.forGetter(Forge6GrindingBallRecipe::modifiers)
        ).apply(instance, Forge6GrindingBallRecipe::new));

    private static final PacketCodec<Forge6GrindingBallRecipe> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.RESOURCE_LOCATION, Forge6GrindingBallRecipe::item,
        GrindingBallData.STREAM_CODEC, Forge6GrindingBallRecipe::modifiers,
        Forge6GrindingBallRecipe::new);

    private Forge6GrindingBallRecipe(ResourceLocation item, GrindingBallData modifiers) {
        this(null, item, modifiers);
    }

    private @Nullable Item ballItem() {
        return ForgeRegistries.ITEMS.getValue(item);
    }

    public static @Nullable GrindingBallData forItem(@Nullable Level level, Item item) {
        var current = GrindingBallData.DATA_MAP_TYPE.get(level, item);
        if (current != null || item == Items.AIR) {
            return current;
        }
        return recipes(level).filter(recipe -> recipe.ballItem() == item)
            .min(Comparator.comparing(Forge6GrindingBallRecipe::getId))
            .map(Forge6GrindingBallRecipe::modifiers).orElse(null);
    }

    public static Stream<Item> items(@Nullable Level level) {
        return recipes(level).map(Forge6GrindingBallRecipe::ballItem)
            .filter(item -> item != null && item != Items.AIR);
    }

    private static Stream<Forge6GrindingBallRecipe> recipes(@Nullable Level level) {
        return level == null ? Stream.empty()
            : level.getRecipeManager().getAllRecipesFor(EIORecipes.FORGE6_GRINDING_BALL.type().get()).stream();
    }

    @Override
    public boolean matches(Container input, Level level) {
        var ball = ballItem();
        return ball != null && ball != Items.AIR && input.getContainerSize() > 0 && input.getItem(0).is(ball);
    }

    @Override
    public ItemStack assemble(Container input, RegistryAccess registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public ResourceLocation getId() {
        return Objects.requireNonNull(id);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return EIORecipes.FORGE6_GRINDING_BALL.serializer().get();
    }

    @Override
    public RecipeType<?> getType() {
        return EIORecipes.FORGE6_GRINDING_BALL.type().get();
    }

    public static final class Serializer extends CodecRecipeSerializer<Forge6GrindingBallRecipe> {
        @Override
        public MapCodec<Forge6GrindingBallRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<Forge6GrindingBallRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        @Override
        protected Forge6GrindingBallRecipe withId(Forge6GrindingBallRecipe recipe, ResourceLocation id) {
            return new Forge6GrindingBallRecipe(id, recipe.item(), recipe.modifiers());
        }
    }
}
