package com.enderio.enderio.foundation.soul;

import com.enderio.core.common.recipes.WrappedShapedRecipe;
import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.api.soul.SoulBoundUtils;
import com.enderio.enderio.init.EIORecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Optional;

/**
 * Based upon BackpackUpgradeRecipe from Sophisticated Backpacks. Thanks!
 */
public class ShapedEntityStorageRecipe extends WrappedShapedRecipe {
    
    public ShapedEntityStorageRecipe(ShapedRecipe recipe) {
        super(recipe);
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess lookupProvider) {
        ItemStack result = getWrapped().assemble(container, lookupProvider);

        getItemStoringEntity(container).ifPresent(itemStack -> {
            var inputSoulStorage = Objects.requireNonNull(itemStack.getCapability(EnderIOCapabilities.SOUL_BINDABLE_ITEM).orElse(null));
            SoulBoundUtils.tryBindSoul(result, inputSoulStorage.getBoundSoul());
        });
        
        return result;
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        // Only let this match if there is an entity storage to pull from
        return getItemStoringEntity(inv).isPresent() && super.matches(inv, level);
    }

    private Optional<ItemStack> getItemStoringEntity(CraftingContainer container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            var soulStorage = stack.getCapability(EnderIOCapabilities.SOUL_BINDABLE_ITEM).orElse(null);
            if (soulStorage != null && soulStorage.hasSoul()) {
                return Optional.of(stack);
            }
        }

        return Optional.empty();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return EIORecipes.SHAPED_ENTITY_STORAGE.get();
    }
}
