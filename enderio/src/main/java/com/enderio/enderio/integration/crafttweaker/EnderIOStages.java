package com.enderio.enderio.integration.crafttweaker;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.enderio.enderio.integration.gamestages.StageRules;
import com.enderio.enderio.integration.gamestages.StageRules.Kind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister
@ZenCodeType.Name("mods.enderio.EnderIOStages")
public final class EnderIOStages {
    private EnderIOStages() {}

    @ZenCodeType.Method
    public static void requireRecipe(ResourceLocation recipe, String stage) {
        require(Kind.RECIPE, recipe, stage);
    }

    @ZenCodeType.Method
    public static void clearRecipe(ResourceLocation recipe) {
        clear(Kind.RECIPE, recipe);
    }

    @ZenCodeType.Method
    public static void requireRecipeType(ResourceLocation type, String stage) {
        require(Kind.RECIPE_TYPE, type, stage);
    }

    @ZenCodeType.Method
    public static void clearRecipeType(ResourceLocation type) {
        clear(Kind.RECIPE_TYPE, type);
    }

    @ZenCodeType.Method
    public static void requireBlock(ResourceLocation block, String stage) {
        require(Kind.BLOCK, block, stage);
    }

    @ZenCodeType.Method
    public static void clearBlock(ResourceLocation block) {
        clear(Kind.BLOCK, block);
    }

    @ZenCodeType.Method
    public static void requireItem(ResourceLocation item, String stage) {
        require(Kind.ITEM, item, stage);
    }

    @ZenCodeType.Method
    public static void clearItem(ResourceLocation item) {
        clear(Kind.ITEM, item);
    }

    @ZenCodeType.Method
    public static void requireAction(ResourceLocation action, String stage) {
        require(Kind.ACTION, action, stage);
    }

    @ZenCodeType.Method
    public static void clearAction(ResourceLocation action) {
        clear(Kind.ACTION, action);
    }

    @ZenCodeType.Method
    public static void setWorldStage(String stage, boolean unlocked) {
        CraftTweakerAPI.apply(new EnderIOAction("Setting Ender IO world stage " + stage + " to " + unlocked, () -> {
            StageRules.validateStage(stage);
            StageRules.setWorldStage(manager(), stage, unlocked);
        }));
    }

    private static void require(Kind kind, ResourceLocation target, String stage) {
        CraftTweakerAPI.apply(new EnderIOAction("Requiring stage " + stage + " for Ender IO " + kind + " " + target, () -> {
            StageRules.validateStage(stage);
            StageRules.validateTarget(kind, target);
            StageRules.require(manager(), kind, target, stage);
        }));
    }

    private static void clear(Kind kind, ResourceLocation target) {
        CraftTweakerAPI.apply(new EnderIOAction("Clearing Ender IO stage rules for " + kind + " " + target,
                () -> StageRules.clear(manager(), kind, target)));
    }

    private static RecipeManager manager() {
        return CraftTweakerAPI.getAccessibleElementsProvider().recipeManager();
    }
}
