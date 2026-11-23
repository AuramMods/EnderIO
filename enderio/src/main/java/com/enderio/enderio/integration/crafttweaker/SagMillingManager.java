package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker.api.tag.CraftTweakerTagRegistry;
import com.enderio.core.common.recipes.OutputStack;
import com.enderio.enderio.content.machines.sag_mill.SagMillingRecipe;
import com.enderio.enderio.init.EIORecipes;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.openzen.zencode.java.ZenCodeType;

import java.util.Arrays;
import java.util.List;

@ZenRegister
@ZenCodeType.Name("mods.enderio.SagMillingManager")
public final class SagMillingManager extends ProcessingRecipeManager<SagMillingRecipe> {
    @Override
    public RecipeType<SagMillingRecipe> getRecipeType() {
        return EIORecipes.SAG_MILLING.type().get();
    }

    @ZenCodeType.Method
    public void addRecipe(String name, IIngredient input, int energy, String bonusType, SagOutput... outputs) {
        add(create(scriptId(name), input, energy, bonusType, outputs));
    }

    @ZenCodeType.Method
    public void replaceRecipe(String id, IIngredient input, int energy, String bonusType, SagOutput... outputs) {
        replace(create(new ResourceLocation(id), input, energy, bonusType, outputs));
    }

    @Override
    protected List<OutputStack> outputs(SagMillingRecipe recipe) {
        return recipe.outputs().stream().filter(output -> output.chance() > 0)
                .map(SagMillingRecipe.OutputItem::getItemStack).filter(stack -> !stack.isEmpty())
                .map(OutputStack::of).toList();
    }

    @Override
    protected boolean matchesOutput(SagMillingRecipe recipe, IIngredient filter) {
        var itemTags = CraftTweakerTagRegistry.INSTANCE.knownTagManager(Registries.ITEM);
        return recipe.outputs().stream().filter(output -> output.chance() > 0).anyMatch(output ->
                output.output().map(stack -> filter.matches(IItemStack.of(stack)),
                        tagged -> tagged.count() > 0 && itemTags.tag(tagged.itemTag()).elements().stream()
                                .anyMatch(item -> filter.matches(IItemStack.of(new ItemStack(item, tagged.count()))))));
    }

    private static SagMillingRecipe create(ResourceLocation id, IIngredient input, int energy, String bonusType,
            SagOutput[] outputs) {
        var ingredient = input.asVanillaIngredient();
        if (ingredient.isEmpty() || outputs.length == 0) {
            throw new IllegalArgumentException("SAG milling needs a nonempty input and at least one output");
        }
        var mode = SagMillingRecipe.BonusType.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(bonusType)).result()
                .orElseThrow(() -> new IllegalArgumentException("SAG bonus must be none, chance_only, or multiply_output"));
        return new SagMillingRecipe(ingredient, Arrays.stream(outputs).map(SagOutput::value).toList(),
                positive(energy, "Energy"), mode, id);
    }
}
