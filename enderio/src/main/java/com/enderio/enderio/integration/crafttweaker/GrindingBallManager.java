package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.enderio.enderio.api.components.GrindingBallData;
import com.enderio.enderio.content.machines.sag_mill.Forge6GrindingBallRecipe;
import com.enderio.enderio.init.EIORecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister
@ZenCodeType.Name("mods.enderio.GrindingBallManager")
public final class GrindingBallManager extends ProcessingRecipeManager<Forge6GrindingBallRecipe> {
    @Override
    public RecipeType<Forge6GrindingBallRecipe> getRecipeType() {
        return EIORecipes.FORGE6_GRINDING_BALL.type().get();
    }

    @ZenCodeType.Method
    public void addRecipe(String name, IItemStack item, float grinding, float chance, float power, int durability) {
        add(create(scriptId(name), item, grinding, chance, power, durability));
    }

    @ZenCodeType.Method
    public void replaceRecipe(String id, IItemStack item, float grinding, float chance, float power, int durability) {
        replace(create(new ResourceLocation(id), item, grinding, chance, power, durability));
    }

    private static Forge6GrindingBallRecipe create(ResourceLocation id, IItemStack item, float grinding, float chance,
            float power, int durability) {
        if (item.isEmpty() || !Float.isFinite(grinding) || grinding < 0 || !Float.isFinite(chance) || chance < 0
                || !Float.isFinite(power) || power <= 0) {
            throw new IllegalArgumentException("Grinding balls need an item, nonnegative grinding and chance multipliers, and positive power");
        }
        var itemId = BuiltInRegistries.ITEM.getKey(item.getInternal().getItem());
        return new Forge6GrindingBallRecipe(id, itemId,
                new GrindingBallData(grinding, chance, power, positive(durability, "Durability")));
    }
}
