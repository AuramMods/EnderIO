package com.enderio.enderio.init;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.enderio.api.conduits.ConduitIngredient;
import com.enderio.enderio.api.soul.binding.ingredients.AnySoulBindableIngredient;
import com.enderio.enderio.api.soul.binding.ingredients.EmptySoulBindableIngredient;
import com.enderio.enderio.api.soul.binding.ingredients.FilledSoulStorageIngredient;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

public class EIOIngredientTypes {
    public static void register(IEventBus bus) { bus.addListener(EIOIngredientTypes::registerSerializers); }

    private static void registerSerializers(RegisterEvent event) {
        if (!event.getRegistryKey().equals(ForgeRegistries.Keys.RECIPE_SERIALIZERS)) return;
        CraftingHelper.register(EnderIOAPI.rl("empty_soul_storage"), EmptySoulBindableIngredient.TYPE);
        CraftingHelper.register(EnderIOAPI.rl("filled_soul_storage"), FilledSoulStorageIngredient.TYPE);
        CraftingHelper.register(EnderIOAPI.rl("any_soul_storage"), AnySoulBindableIngredient.TYPE);
        CraftingHelper.register(EnderIOAPI.rl("conduit"), ConduitIngredient.TYPE);
    }
}
