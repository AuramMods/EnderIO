package com.enderio.enderio.content.machines.obelisks.weather;

import net.minecraft.resources.ResourceLocation;
import com.enderio.core.common.recipes.OutputStack;
import com.enderio.enderio.foundation.MachineRecipe;
import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.core.common.recipes.RecipeRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.RegistryAccess;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.FireworkRocketItem;
import com.enderio.core.common.recipes.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;
import java.util.function.IntFunction;

public record WeatherChangeRecipe(FluidStack fluid, WeatherMode mode, ResourceLocation id)
        implements MachineRecipe<WeatherChangeRecipe.Input> {

    public WeatherChangeRecipe(FluidStack fluid, WeatherMode mode) { this(fluid, mode, null); }

    public WeatherChangeRecipe withId(ResourceLocation id) { return new WeatherChangeRecipe(fluid, mode, java.util.Objects.requireNonNull(id)); }


    @Override
    public int getBaseEnergyCost() {
        return 0;
    }

    @Override
    public List<OutputStack> craft(Input container, RegistryAccess registryAccess) {
        return List.of();
    }

    @Override
    public List<OutputStack> getResultStacks(RegistryAccess registryAccess) {
        return List.of();
    }

    @Override
    public boolean matches(Input input, Level level) {
        return input.fluid().getFluid() == fluid.getFluid() && input.fluid.getAmount() >= fluid.getAmount();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return RecipeRegistry.serializer(EnderIOAPI.rl("weather_change"));
    }

    @Override
    public RecipeType<?> getType() {
        return RecipeRegistry.type(EnderIOAPI.rl("weather_change"));
    }

    public record Input(FluidStack fluid) implements RecipeInput {

        @Override
        public ItemStack getItem(int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    public enum WeatherMode implements StringRepresentable {
        CLEAR("clear", fireworks(new int[] {DyeColor.YELLOW.getFireworkColor(), DyeColor.WHITE.getFireworkColor()},
            new int[] {DyeColor.WHITE.getFireworkColor()}, false, true)),
        RAIN("rain", fireworks(new int[] {DyeColor.BLUE.getFireworkColor(), DyeColor.LIGHT_BLUE.getFireworkColor()},
            new int[] {DyeColor.LIGHT_BLUE.getFireworkColor()}, false, true)),
        LIGHTNING("lightning", fireworks(new int[] {DyeColor.BLUE.getFireworkColor(), DyeColor.YELLOW.getFireworkColor()},
            new int[] {DyeColor.YELLOW.getFireworkColor()}, false, true));

        public static final Codec<WeatherMode> CODEC = StringRepresentable.fromEnum(WeatherMode::values);
        public static final IntFunction<WeatherMode> BY_ID = ByIdMap.continuous(Enum::ordinal, values(),
                ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final PacketCodec<WeatherMode> STREAM_CODEC = PacketCodecs.idMapper(BY_ID,
                Enum::ordinal);
        private final String type;
        private final CompoundTag fireworks;
        public static final CompoundTag SURPRISE = fireworks(new int[] {DyeColor.RED.getFireworkColor(),
            DyeColor.ORANGE.getFireworkColor(), DyeColor.YELLOW.getFireworkColor(), DyeColor.GREEN.getFireworkColor(),
            DyeColor.BLUE.getFireworkColor(), DyeColor.PURPLE.getFireworkColor()}, new int[0], true, true);
        public static final CompoundTag SURPRISE_2 = fireworks(new int[] {DyeColor.LIGHT_BLUE.getFireworkColor(),
            DyeColor.PINK.getFireworkColor(), DyeColor.WHITE.getFireworkColor()}, new int[0], true, true);

        private static CompoundTag fireworks(int[] colors, int[] fadeColors, boolean flicker, boolean trail) {
            var explosion = new CompoundTag();
            explosion.putByte("Type", (byte) FireworkRocketItem.Shape.LARGE_BALL.getId());
            explosion.putIntArray("Colors", colors);
            explosion.putIntArray("FadeColors", fadeColors);
            explosion.putBoolean("Flicker", flicker);
            explosion.putBoolean("Trail", trail);
            var explosions = new ListTag();
            explosions.add(explosion);
            var fireworks = new CompoundTag();
            fireworks.putByte("Flight", (byte) 2);
            fireworks.put("Explosions", explosions);
            return fireworks.copy();
        }

        WeatherMode(String type, CompoundTag fireworks) {
            this.type = type;
            this.fireworks = fireworks;
        }

        @Override
        public String getSerializedName() {
            return type;
        }

        public CompoundTag getFireworks() {
            return fireworks.copy();
        }
    }

    public static class Serializer extends com.enderio.core.common.recipes.CodecRecipeSerializer<WeatherChangeRecipe> {
        public static final MapCodec<WeatherChangeRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst
                .group(com.enderio.core.common.recipes.RecipeStackCodecs.FLUID.fieldOf("fluid").forGetter(WeatherChangeRecipe::fluid),
                        WeatherMode.CODEC.fieldOf("mode").forGetter(WeatherChangeRecipe::mode))
                .apply(inst, WeatherChangeRecipe::new));

        public static final PacketCodec<WeatherChangeRecipe> STREAM_CODEC = PacketCodec
                .composite(PacketCodecs.FLUID_STACK, WeatherChangeRecipe::fluid, WeatherMode.STREAM_CODEC,
                        WeatherChangeRecipe::mode, WeatherChangeRecipe::new);

        @Override
        protected WeatherChangeRecipe withId(WeatherChangeRecipe recipe, ResourceLocation id) { return recipe.withId(id); }

        @Override
        public MapCodec<WeatherChangeRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<WeatherChangeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
