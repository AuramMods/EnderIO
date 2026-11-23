package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.ingredient.type.WrappingIIngredient;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.enderio.enderio.api.EnderIORegistries;
import com.enderio.enderio.api.conduits.ConduitIngredient;
import com.enderio.enderio.api.soul.binding.ingredients.AnySoulBindableIngredient;
import com.enderio.enderio.api.soul.binding.ingredients.EmptySoulBindableIngredient;
import com.enderio.enderio.api.soul.binding.ingredients.FilledSoulStorageIngredient;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister
@ZenCodeType.Name("mods.enderio.EnderIOIngredients")
public final class EnderIOIngredients {
    private EnderIOIngredients() {}

    @ZenCodeType.Method
    public static IIngredient emptySoulStorage(IItemStack item) {
        return wrap(EmptySoulBindableIngredient.of(storage(item)), "emptySoulStorage", item.getCommandString());
    }

    @ZenCodeType.Method
    public static IIngredient filledSoulStorage(IItemStack item) {
        return wrap(FilledSoulStorageIngredient.of(storage(item)), "filledSoulStorage", item.getCommandString());
    }

    @ZenCodeType.Method
    public static IIngredient anySoulStorage(IItemStack item) {
        return wrap(AnySoulBindableIngredient.of(storage(item)), "anySoulStorage", item.getCommandString());
    }

    @ZenCodeType.Method
    public static IIngredient conduit(ResourceLocation variant) {
        return wrap(conduitIngredient(variant), "conduit", "<resource:" + variant + ">");
    }

    static Ingredient conduitIngredient(ResourceLocation variant) {
        return CraftTweakerAPI.getAccessibleElementsProvider().registryAccess(registries -> {
            var key = ResourceKey.create(EnderIORegistries.Keys.CONDUIT, variant);
            var holder = registries.registryOrThrow(EnderIORegistries.Keys.CONDUIT).getHolder(key)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown Ender IO conduit variant " + variant));
            return ConduitIngredient.of(holder);
        });
    }

    private static Item storage(IItemStack item) {
        if (item.isEmpty()) {
            throw new IllegalArgumentException("Soul storage ingredient needs a nonempty item");
        }
        return item.getInternal().getItem();
    }

    private static IIngredient wrap(Ingredient ingredient, String method, String argument) {
        return new WrappingIIngredient(ingredient, "mods.enderio.EnderIOIngredients." + method + "(" + argument + ")");
    }
}
