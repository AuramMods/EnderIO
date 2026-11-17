package com.enderio.core.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {
    /** The native manager replaces this map after loading or receiving a recipe collection. */
    @Accessor("recipes")
    Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> endercore$getRecipes();
}
