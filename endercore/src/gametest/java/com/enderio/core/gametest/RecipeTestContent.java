package com.enderio.core.gametest;

import com.enderio.enderio.content.fire_crafting.FireCraftingRecipe;
import com.enderio.enderio.content.machines.vat.FermentingRecipe;
import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
import com.enderio.enderio.content.machines.slicer.SlicingRecipe;
import com.enderio.enderio.content.machines.obelisks.weather.WeatherChangeRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

public final class RecipeTestContent {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, "enderio");
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, "enderio");
    public static void register(IEventBus bus) {
        SERIALIZERS.register("alloy_smelting", AlloySmeltingRecipe.Serializer::new);
        SERIALIZERS.register("slicing", SlicingRecipe.Serializer::new);
        SERIALIZERS.register("fire_crafting", FireCraftingRecipe.Serializer::new);
        SERIALIZERS.register("vat_fermenting", FermentingRecipe.Serializer::new);
        SERIALIZERS.register("weather_change", WeatherChangeRecipe.Serializer::new);
        TYPES.register("alloy_smelting", () -> new RecipeType<AlloySmeltingRecipe>() {});
        TYPES.register("slicing", () -> new RecipeType<SlicingRecipe>() {});
        TYPES.register("fire_crafting", () -> new RecipeType<FireCraftingRecipe>() {});
        TYPES.register("vat_fermenting", () -> new RecipeType<FermentingRecipe>() {});
        TYPES.register("weather_change", () -> new RecipeType<WeatherChangeRecipe>() {});
        SERIALIZERS.register(bus);
        TYPES.register(bus);
    }
}
