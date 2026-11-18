package com.enderio.enderio.compat.jei_machines_to_merge;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.foundation.souldata.EngineSoul;
import com.enderio.enderio.init.EIOItems;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.advanced.ISimpleRecipeManagerPlugin;
import net.minecraft.world.item.SpawnEggItem;

import java.util.List;
import java.util.Optional;

/** Forge can start JEI before the client receives fluid tags or custom soul data. */
final class SoulEngineRecipeLookup implements ISimpleRecipeManagerPlugin<EngineSoul.SoulData> {
    @Override
    public boolean isHandledInput(ITypedIngredient<?> ingredient) {
        return !getRecipesForInput(ingredient).isEmpty();
    }

    @Override
    public boolean isHandledOutput(ITypedIngredient<?> ingredient) {
        return false;
    }

    @Override
    public List<EngineSoul.SoulData> getRecipesForInput(ITypedIngredient<?> ingredient) {
        var fluid = ingredient.getIngredient(ForgeTypes.FLUID_STACK);
        if (fluid.isPresent()) {
            return getAllRecipes().stream().filter(rule -> rule.matchesFuel(fluid.get().getFluid())).toList();
        }
        return ingredient.getItemStack().flatMap(stack -> {
            if (stack.getItem() instanceof SpawnEggItem egg) {
                return EngineSoul.ENGINE.matches(egg.getType(null));
            }
            if (stack.is(EIOItems.SOUL_VIAL.get())) {
                var soul = Soul.ITEM_DATA.getOrDefault(stack, Soul.EMPTY);
                if (soul.hasEntity()) return EngineSoul.ENGINE.matches(soul.entityTypeId());
            }
            return Optional.<EngineSoul.SoulData>empty();
        }).stream().toList();
    }

    @Override
    public List<EngineSoul.SoulData> getRecipesForOutput(ITypedIngredient<?> ingredient) {
        return List.of();
    }

    @Override
    public List<EngineSoul.SoulData> getAllRecipes() {
        return List.copyOf(EngineSoul.ENGINE.map.values());
    }
}
