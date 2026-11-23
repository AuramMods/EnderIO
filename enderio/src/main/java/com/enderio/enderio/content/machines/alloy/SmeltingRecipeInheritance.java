package com.enderio.enderio.content.machines.alloy;

import com.enderio.core.common.recipes.SizedIngredient;
import com.enderio.enderio.EnderIO;
import com.enderio.enderio.api.EnderIOAPI;
import com.mojang.logging.LogUtils;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Derives alloy recipes from the current furnace recipes during a server resource reload. */
@Mod.EventBusSubscriber(modid = EnderIO.MOD_ID)
public final class SmeltingRecipeInheritance {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<RecipeManager, Reload> SCRIPT_RELOADS = Collections.synchronizedMap(new WeakHashMap<>());

    public static ResourceLocation inheritedId(ResourceLocation original) {
        return EnderIOAPI.rl("smelting/" + original.getNamespace() + "/" + original.getPath());
    }

    public static void inherit(RecipeManager manager, int energyPerItem) {
        var recipes = new ArrayList<>(manager.getRecipes());
        var generated = new HashMap<ResourceLocation, Inherited>();
        var shadowed = new HashSet<ResourceLocation>();
        for (var smelting : manager.getAllRecipesFor(RecipeType.SMELTING)) {
            var id = inheritedId(smelting.getId());
            // An explicitly supplied datapack recipe owns its ID.
            if (manager.byKey(id).isPresent()) {
                shadowed.add(id);
                continue;
            }
            var alloy = derive(smelting, id, energyPerItem);
            if (alloy != null) {
                recipes.add(alloy);
                generated.put(id, new Inherited(smelting, alloy));
            }
        }
        if (!generated.isEmpty()) {
            manager.replaceRecipes(recipes);
        }
        if (ModList.get().isLoaded("crafttweaker")) {
            SCRIPT_RELOADS.put(manager, new Reload(energyPerItem, generated, shadowed, new HashSet<>()));
        }
    }

    public static void exclude(RecipeManager manager, ResourceLocation furnaceRecipe) {
        var reload = SCRIPT_RELOADS.get(manager);
        if (reload == null) {
            throw new IllegalStateException("Furnace exclusions must be set while Ender IO recipes are reloading");
        }
        reload.excluded().add(inheritedId(furnaceRecipe));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void afterCraftTweaker(AddReloadListenerEvent event) {
        if (!ModList.get().isLoaded("crafttweaker")) {
            return;
        }
        var manager = event.getServerResources().getRecipeManager();
        // CraftTweaker installs its server script listener at LOW priority.
        event.addListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager resources, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void ignored, ResourceManager resources, ProfilerFiller profiler) {
                reconcile(manager);
            }
        });
    }

    private static void reconcile(RecipeManager manager) {
        var reload = SCRIPT_RELOADS.remove(manager);
        if (reload == null) {
            return;
        }
        var recipes = manager.getRecipes().stream().collect(Collectors.toMap(Recipe::getId, Function.identity()));
        var smelting = manager.getAllRecipesFor(RecipeType.SMELTING);
        var sources = smelting.stream().collect(Collectors.toMap(Recipe::getId, Function.identity()));
        boolean changed = false;
        for (var entry : reload.generated().entrySet()) {
            var id = entry.getKey();
            var previous = entry.getValue();
            if (recipes.get(id) != previous.generated()) {
                // A script's removal or explicit replacement wins over inheritance.
                continue;
            }
            var source = sources.get(previous.source().getId());
            if (source != previous.source() || reload.excluded().contains(id)) {
                recipes.remove(id);
                changed = true;
                if (source != null && !reload.excluded().contains(id)) {
                    var alloy = derive(source, id, reload.energyPerItem());
                    if (alloy != null) {
                        recipes.put(id, alloy);
                    }
                }
            }
        }
        for (var source : smelting) {
            var id = inheritedId(source.getId());
            if (reload.generated().containsKey(id) || reload.shadowed().contains(id)
                    || reload.excluded().contains(id) || recipes.containsKey(id)) {
                continue;
            }
            var alloy = derive(source, id, reload.energyPerItem());
            if (alloy != null) {
                recipes.put(id, alloy);
                changed = true;
            }
        }
        if (changed) {
            manager.replaceRecipes(recipes.values());
        }
    }

    private static AlloySmeltingRecipe derive(SmeltingRecipe source, ResourceLocation id, int energyPerItem) {
        var result = source.getResultItem(RegistryAccess.EMPTY);
        var ingredients = source.getIngredients();
        if (result.isEmpty() || ingredients.size() != 1 || ingredients.get(0).isEmpty()) {
            LOGGER.warn("[EnderIO] Cannot inherit smelting recipe {}: empty result or invalid ingredient", source.getId());
            return null;
        }
        return new AlloySmeltingRecipe(List.of(new SizedIngredient(ingredients.get(0), 1)),
            result.copy(), energyPerItem, source.getExperience(), true).withId(id);
    }

    private record Inherited(SmeltingRecipe source, AlloySmeltingRecipe generated) {}

    private record Reload(int energyPerItem, Map<ResourceLocation, Inherited> generated,
                          Set<ResourceLocation> shadowed, Set<ResourceLocation> excluded) {}
}
