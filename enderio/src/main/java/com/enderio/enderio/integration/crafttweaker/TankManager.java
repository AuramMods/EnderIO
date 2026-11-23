package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.fluid.CTFluidIngredient;
import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker.api.tag.CraftTweakerTagRegistry;
import com.enderio.core.common.recipes.OutputStack;
import com.enderio.enderio.content.storage.fluid_tank.TankRecipe;
import com.enderio.enderio.init.EIORecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.openzen.zencode.java.ZenCodeType;

import java.util.ArrayList;
import java.util.List;

@ZenRegister
@ZenCodeType.Name("mods.enderio.TankManager")
public final class TankManager extends FluidOutputRecipeManager<TankRecipe> {
    @Override
    public RecipeType<TankRecipe> getRecipeType() {
        return EIORecipes.TANK.type().get();
    }

    @ZenCodeType.Method
    public void addRecipe(String name, String mode, IIngredient input, IItemStack output, CTFluidIngredient fluid) {
        add(create(scriptId(name), mode, input, output, fluid));
    }

    @ZenCodeType.Method
    public void replaceRecipe(String id, String mode, IIngredient input, IItemStack output, CTFluidIngredient fluid) {
        replace(create(new ResourceLocation(id), mode, input, output, fluid));
    }

    @Override
    protected List<OutputStack> outputs(TankRecipe recipe) {
        var output = new ArrayList<OutputStack>();
        output.add(OutputStack.of(recipe.output()));
        if (recipe.mode() == TankRecipe.Mode.EMPTY) {
            var tags = CraftTweakerTagRegistry.INSTANCE.knownTagManager(Registries.FLUID);
            BuiltInRegistries.FLUID.stream().filter(fluid -> fluid != Fluids.EMPTY)
                    .map(fluid -> new FluidStack(fluid, recipe.fluid().amount()))
                    .filter(stack -> recipe.fluid().ingredient().test(stack, (fluid, tag) -> tags.tag(tag).contains(fluid)))
                    .map(OutputStack::of).forEach(output::add);
        }
        return output;
    }

    private static TankRecipe create(ResourceLocation id, String mode, IIngredient input, IItemStack output,
            CTFluidIngredient fluid) {
        var ingredient = input.asVanillaIngredient();
        if (ingredient.isEmpty()) {
            throw new IllegalArgumentException("Tank recipes need a nonempty item ingredient");
        }
        return new TankRecipe(ingredient, outputStack(output), FluidRecipeArguments.ingredient(fluid),
                FluidRecipeArguments.option(TankRecipe.Mode.CODEC, mode), id);
    }
}
