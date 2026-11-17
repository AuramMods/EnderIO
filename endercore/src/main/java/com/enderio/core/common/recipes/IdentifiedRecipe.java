package com.enderio.core.common.recipes;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import java.util.Objects;

/** Recipe data may be constructed for datagen before its resource ID is assigned. Loaded recipes always have an ID. */
public interface IdentifiedRecipe<C extends Container> extends Recipe<C> {
    ResourceLocation id();
    @Override
    default ResourceLocation getId() { return Objects.requireNonNull(id(), "Recipe data has not been assigned a resource ID"); }
}
