package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.fluid.IFluidStack;
import com.enderio.enderio.content.machines.obelisks.weather.WeatherChangeRecipe;
import com.enderio.enderio.init.EIORecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister
@ZenCodeType.Name("mods.enderio.WeatherChangeManager")
public final class WeatherChangeManager extends ProcessingRecipeManager<WeatherChangeRecipe> {
    @Override
    public RecipeType<WeatherChangeRecipe> getRecipeType() {
        return EIORecipes.WEATHER_CHANGE.type().get();
    }

    @ZenCodeType.Method
    public void addRecipe(String name, IFluidStack fluid, String mode) {
        add(create(scriptId(name), fluid, mode));
    }

    @ZenCodeType.Method
    public void replaceRecipe(String id, IFluidStack fluid, String mode) {
        replace(create(new ResourceLocation(id), fluid, mode));
    }

    private static WeatherChangeRecipe create(ResourceLocation id, IFluidStack fluid, String mode) {
        return new WeatherChangeRecipe(FluidRecipeArguments.stack(fluid),
                FluidRecipeArguments.option(WeatherChangeRecipe.WeatherMode.CODEC, mode), id);
    }
}
