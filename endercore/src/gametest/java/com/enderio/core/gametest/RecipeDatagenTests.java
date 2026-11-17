package com.enderio.core.gametest;

import com.enderio.core.common.recipes.SizedIngredient;
import com.enderio.core.data.recipe.RecipeOutput;
import com.enderio.core.data.recipe.StackShapedRecipeBuilder;
import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.ModLoadedCondition;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class RecipeDatagenTests {
    private static ResourceLocation id(String path) { return ItemDataTestContent.id(path); }

    private static ItemStack taggedOutput(int count) {
        var stack = new ItemStack(Items.DIAMOND, count);
        stack.getOrCreateTag().putString("enderio:conduit", "enderio:energy");
        stack.getOrCreateTag().putIntArray("typed", new int[] { 1, 5, 9 });
        stack.getOrCreateTag().putByte("flag", (byte) 1);
        return stack;
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void customRecipeOutputRoundTripsNativeLoaderConditionsAndAdvancement(GameTestHelper helper) {
        var records = new ArrayList<FinishedRecipe>();
        var writer = new RecipeOutput(records::add, helper.getLevel().registryAccess());
        var result = taggedOutput(3);
        var recipe = new AlloySmeltingRecipe(List.of(SizedIngredient.of(Items.IRON_INGOT, 2)), result, 2400, 0.7F);
        var advancement = Advancement.Builder.advancement()
            .addCriterion("has_iron", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT))
            .build(id("recipes/test_alloy"));
        writer.accept(id("generated_alloy"), recipe, advancement, new ModLoadedCondition("forge"));
        writer.accept(id("absent_mod_alloy"), recipe, null, new ModLoadedCondition("endercore_tests_missing_mod"));
        var saved = records.get(0);
        var json = saved.serializeRecipe();
        helper.assertTrue(CraftingHelper.processConditions(json, "conditions", ICondition.IContext.EMPTY),
            "Installed Forge condition rejected generated recipe");
        helper.assertTrue(!CraftingHelper.processConditions(records.get(1).serializeRecipe(), "conditions", ICondition.IContext.EMPTY),
            "Absent optional mod condition admitted generated recipe");
        var decoded = (AlloySmeltingRecipe) RecipeManager.fromJson(saved.getId(), json);
        helper.assertTrue(decoded.getId().equals(id("generated_alloy")) && decoded.inputs().get(0).count() == 2
            && decoded.energy() == 2400 && decoded.experience() == 0.7F && decoded.output().getCount() == 3
            && ItemStack.isSameItemSameTags(decoded.output(), result), "Generated custom recipe lost native payload");
        helper.assertTrue(saved.getAdvancementId().equals(advancement.getId())
            && saved.serializeAdvancement().getAsJsonObject("criteria").has("has_iron"), "Custom advancement lost");
        helper.assertTrue(records.get(1).getAdvancementId() == null && records.get(1).serializeAdvancement() == null,
            "Recipe without advancement produced one");
        // The finished record is a snapshot; writing or changing the original cannot mutate it.
        result.getOrCreateTag().putInt("mutated", 1);
        json.getAsJsonObject("output").addProperty("count", 99);
        var again = (AlloySmeltingRecipe) RecipeManager.fromJson(saved.getId(), saved.serializeRecipe());
        helper.assertTrue(again.output().getCount() == 3 && !again.output().getTag().contains("mutated"),
            "Finished recipe aliases source or serialized JSON");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void shapedOutputRetainsNbtQuantityCriteriaAndNativeMatching(GameTestHelper helper) {
        var records = new ArrayList<FinishedRecipe>();
        var result = taggedOutput(8);
        var expected = result.copy();
        var builder = StackShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
            .pattern("II").pattern("II").define('I', Items.IRON_INGOT)
            .unlockedBy("has_iron", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT));
        result.setCount(1);
        result.getOrCreateTag().putString("enderio:conduit", "enderio:fluid");
        builder.save(records::add, id("generated_shaped"));
        var saved = records.get(0);
        var decoded = (net.minecraft.world.item.crafting.ShapedRecipe) RecipeManager.fromJson(saved.getId(), saved.serializeRecipe());
        var actual = decoded.getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(actual.getCount() == 8 && ItemStack.isSameItemSameTags(actual, expected),
            "Native shaped serializer lost output NBT/count or builder retained caller mutation");
        helper.assertTrue(decoded.getWidth() == 2 && decoded.getHeight() == 2 && decoded.getIngredients().size() == 4
            && decoded.getIngredients().stream().allMatch(i -> i.test(new ItemStack(Items.IRON_INGOT))),
            "Shaped pattern or ingredients changed");
        var advancement = saved.serializeAdvancement();
        helper.assertTrue(advancement.getAsJsonObject("criteria").has("has_iron")
            && advancement.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString().equals(saved.getId().toString()),
            "Native recipe unlock or reward changed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cookingOutputRetainsMultipleItemsAndTypedNbt(GameTestHelper helper) {
        var records = new ArrayList<FinishedRecipe>();
        var writer = new RecipeOutput(records::add, helper.getLevel().registryAccess());
        var result = taggedOutput(2);
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(Items.IRON_INGOT), RecipeCategory.MISC,
                Items.DIAMOND, 0.25F, 200)
            .unlockedBy("has_iron", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT))
            .save(writer.withResult(result), id("generated_cooking"));
        var saved = records.get(0);
        var decoded = (net.minecraft.world.item.crafting.SmeltingRecipe) RecipeManager.fromJson(saved.getId(), saved.serializeRecipe());
        var actual = decoded.getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(actual.getCount() == 2 && ItemStack.isSameItemSameTags(actual, result)
            && decoded.getCookingTime() == 200 && decoded.getExperience() == 0.25F, "Cooking output/timing changed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeFinishedRecipesPassThroughAndEmptyOutputsFail(GameTestHelper helper) {
        var nativeRecords = new ArrayList<FinishedRecipe>();
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, Items.DIAMOND).pattern("II").define('I', Items.IRON_INGOT)
            .unlockedBy("has_iron", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT))
            .save(nativeRecords::add, id("native_pass_through"));
        var received = new ArrayList<FinishedRecipe>();
        var writer = new RecipeOutput(received::add, helper.getLevel().registryAccess());
        writer.accept(nativeRecords.get(0));
        helper.assertTrue(received.size() == 1 && received.get(0) == nativeRecords.get(0), "Native recipe changed on forwarding");
        try {
            writer.withResult(ItemStack.EMPTY);
            helper.fail("Empty recipe result accepted");
        } catch (IllegalArgumentException expected) { }
        helper.succeed();
    }
}
