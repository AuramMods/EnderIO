package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.fluid.CTFluidIngredient;
import com.blamejared.crafttweaker.api.fluid.IFluidStack;
import com.blamejared.crafttweaker.api.tag.type.KnownTag;
import com.enderio.enderio.content.machines.vat.FermentingRecipe;
import com.enderio.enderio.init.EIORecipes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeType;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister
@ZenCodeType.Name("mods.enderio.VatFermentingManager")
public final class VatFermentingManager extends FluidOutputRecipeManager<FermentingRecipe> {
    @Override
    public RecipeType<FermentingRecipe> getRecipeType() {
        return EIORecipes.VAT_FERMENTING.type().get();
    }

    @ZenCodeType.Method
    public void addRecipe(String name, CTFluidIngredient input, KnownTag<Item> firstReagent, KnownTag<Item> secondReagent,
            IFluidStack output, int ticks) {
        add(create(scriptId(name), input, firstReagent, secondReagent, output, ticks));
    }

    @ZenCodeType.Method
    public void replaceRecipe(String id, CTFluidIngredient input, KnownTag<Item> firstReagent, KnownTag<Item> secondReagent,
            IFluidStack output, int ticks) {
        replace(create(new ResourceLocation(id), input, firstReagent, secondReagent, output, ticks));
    }

    private static FermentingRecipe create(ResourceLocation id, CTFluidIngredient input, KnownTag<Item> firstReagent,
            KnownTag<Item> secondReagent, IFluidStack output, int ticks) {
        return new FermentingRecipe(FluidRecipeArguments.ingredient(input), firstReagent.getTagKey(),
                secondReagent.getTagKey(), FluidRecipeArguments.stack(output), positive(ticks, "Ticks"), id);
    }
}
