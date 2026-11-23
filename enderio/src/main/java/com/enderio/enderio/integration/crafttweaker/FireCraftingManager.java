package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.tag.type.KnownTag;
import com.enderio.core.common.recipes.OutputStack;
import com.enderio.enderio.content.fire_crafting.FireCraftingRecipe;
import com.enderio.enderio.init.EIORecipes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import org.openzen.zencode.java.ZenCodeType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@ZenRegister
@ZenCodeType.Name("mods.enderio.FireCraftingManager")
public final class FireCraftingManager extends ProcessingRecipeManager<FireCraftingRecipe> {
    @Override
    public RecipeType<FireCraftingRecipe> getRecipeType() {
        return EIORecipes.FIRE_CRAFTING.type().get();
    }

    @ZenCodeType.Method
    public void addRecipe(String name, Block[] bases, KnownTag<Block>[] baseTags, ResourceLocation[] dimensions,
            @ZenCodeType.Nullable Block replacement, FireOutput... results) {
        add(create(scriptId(name), bases, baseTags, dimensions, replacement, results));
    }

    @ZenCodeType.Method
    public void replaceRecipe(String id, Block[] bases, KnownTag<Block>[] baseTags, ResourceLocation[] dimensions,
            @ZenCodeType.Nullable Block replacement, FireOutput... results) {
        replace(create(new ResourceLocation(id), bases, baseTags, dimensions, replacement, results));
    }

    @Override
    protected List<OutputStack> outputs(FireCraftingRecipe recipe) {
        return recipe.results().stream().filter(result -> result.chance() > 0)
                .map(result -> OutputStack.of(result.result().copyWithCount(result.maxCount()))).toList();
    }

    private static FireCraftingRecipe create(ResourceLocation id, Block[] bases, KnownTag<Block>[] baseTags,
            ResourceLocation[] dimensions, Block replacement, FireOutput[] results) {
        if (bases.length + baseTags.length == 0 || dimensions.length == 0 || results.length == 0) {
            throw new IllegalArgumentException("Fire crafting needs at least one base block or tag, dimension, and result");
        }
        return new FireCraftingRecipe(Arrays.stream(results).map(FireOutput::value).toList(), List.of(bases),
                Arrays.stream(baseTags).map(tag -> tag.<TagKey<Block>>getTagKey()).toList(),
                Arrays.stream(dimensions).map(dimension -> ResourceKey.create(Registries.DIMENSION, dimension)).toList(),
                Optional.ofNullable(replacement), id);
    }
}
