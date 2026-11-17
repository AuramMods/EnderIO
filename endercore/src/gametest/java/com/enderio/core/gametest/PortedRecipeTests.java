package com.enderio.core.gametest;

import com.enderio.core.common.recipes.SizedIngredient;
import com.enderio.enderio.content.fire_crafting.FireCraftingRecipe;
import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
import com.enderio.enderio.content.machines.slicer.SlicingRecipe;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.Optional;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class PortedRecipeTests {
    private static ResourceLocation id(String path) { return ItemDataTestContent.id(path); }

    private static AlloySmeltingRecipe alloy() {
        return (AlloySmeltingRecipe) RecipeManager.fromJson(id("alloy"), JsonParser.parseString("""
            {"type":"enderio:alloy_smelting", "inputs":[{"item":"minecraft:iron_ingot","count":2},{"item":"minecraft:gold_ingot"}],
             "output":{"id":"minecraft:diamond","count":3,"tag":"{variant:2,typed:[I;1,2]}"}, "energy":2400,"experience":0.7}
            """).getAsJsonObject());
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeRecipeManagerAndSyncPacketRetainActualRecipeIdsAndPayloads(GameTestHelper helper) {
        var alloy = alloy();
        var slicing = new SlicingRecipe(new ItemStack(Items.DIAMOND), List.of(Ingredient.of(Items.IRON_INGOT), Ingredient.EMPTY,
            Ingredient.of(Items.GOLD_INGOT), Ingredient.EMPTY, Ingredient.EMPTY, Ingredient.EMPTY), 500).withId(id("slice"));
        var fire = new FireCraftingRecipe(List.of(new FireCraftingRecipe.Result(new ItemStack(Items.REDSTONE), 1, 3, 0.5f)),
            List.of(Blocks.STONE), List.of(), List.of(Level.NETHER), Optional.of(Blocks.NETHERRACK)).withId(id("fire"));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ClientboundUpdateRecipesPacket(List.of(alloy, slicing, fire)).write(buffer);
            var packet = new ClientboundUpdateRecipesPacket(buffer);
            helper.assertTrue(buffer.readableBytes() == 0 && packet.getRecipes().size() == 3, "Native recipe packet count/trailing bytes changed");
            var manager = new RecipeManager();
            manager.replaceRecipes(packet.getRecipes());
            var received = (AlloySmeltingRecipe) manager.byKey(alloy.getId()).orElseThrow();
            helper.assertTrue(received.getId().equals(id("alloy")) && received.inputs().get(0).count() == 2 &&
                received.output().getCount() == 3 && ItemStack.isSameItemSameTags(received.output(), alloy.output()) && received.energy() == 2400,
                "Native recipe sync lost identity, quantity or output NBT");
            var syncedSlice = (SlicingRecipe) manager.byKey(id("slice")).orElseThrow();
            helper.assertTrue(syncedSlice.inputs().size() == 6 && syncedSlice.inputs().get(1).isEmpty() && syncedSlice.energy() == 500,
                "Slicer positional inputs changed");
            var syncedFire = (FireCraftingRecipe) manager.byKey(id("fire")).orElseThrow();
            helper.assertTrue(syncedFire.isBaseValid(Blocks.STONE) && !syncedFire.isBaseValid(Blocks.DIRT) &&
                syncedFire.isDimensionValid(Level.NETHER) && !syncedFire.isDimensionValid(Level.OVERWORLD) &&
                syncedFire.blockAfterBurning().orElseThrow() == Blocks.NETHERRACK && syncedFire.results().get(0).maxCount() == 3,
                "Fire crafting data changed through native synchronization");
            manager.replaceRecipes(List.of(received.withId(id("reload"))));
            helper.assertTrue(manager.byKey(id("alloy")).isEmpty() && manager.byKey(id("reload")).isPresent(), "Recipe reload retained stale IDs");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void actualAlloyRecipeMatchesCountsPermutationsAndCopiesOutputs(GameTestHelper helper) {
        var recipe = alloy();
        var valid = new AlloySmeltingRecipe.Input(List.of(new ItemStack(Items.GOLD_INGOT), ItemStack.EMPTY, new ItemStack(Items.IRON_INGOT, 2)), 1);
        helper.assertTrue(recipe.matches(valid, helper.getLevel()), "Valid ingredient permutation rejected");
        var insufficient = new AlloySmeltingRecipe.Input(List.of(new ItemStack(Items.GOLD_INGOT), ItemStack.EMPTY, new ItemStack(Items.IRON_INGOT)), 1);
        helper.assertTrue(!recipe.matches(insufficient, helper.getLevel()), "Insufficient quantity accepted");
        var extra = new AlloySmeltingRecipe.Input(List.of(new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.DIRT), new ItemStack(Items.IRON_INGOT, 2)), 1);
        helper.assertTrue(!recipe.matches(extra, helper.getLevel()), "Unexpected occupied slot accepted");
        var result = recipe.craft(valid, helper.getLevel().registryAccess()).get(0).getItem();
        result.getOrCreateTag().putInt("variant", 999);
        result.setCount(1);
        helper.assertTrue(recipe.output().getCount() == 3 && recipe.output().getTag().getInt("variant") == 2 && valid.getItem(2).getCount() == 2,
            "Crafting result aliases recipe or consumes input view");
        var smelting = new AlloySmeltingRecipe(List.of(SizedIngredient.of(Items.IRON_ORE, 1)), new ItemStack(Items.IRON_INGOT), 500, 0.2f, true).withId(id("smelting"));
        var smeltInput = new AlloySmeltingRecipe.Input(List.of(new ItemStack(Items.IRON_ORE), ItemStack.EMPTY, new ItemStack(Items.IRON_ORE)), 2);
        helper.assertTrue(smelting.matches(smeltInput, helper.getLevel()) && smelting.craft(smeltInput, helper.getLevel().registryAccess()).get(0).getItem().getCount() == 2,
            "Inherited smelting output scaling changed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void actualRecipeInputsUseNativeReadOnlyContainerAndSlicerPositions(GameTestHelper helper) {
        var ingredient = new SerializationTestContent.LabelIngredient("a");
        var recipe = new SlicingRecipe(new ItemStack(Items.DIAMOND), List.of(ingredient, Ingredient.EMPTY, Ingredient.of(Items.GOLD_INGOT),
            Ingredient.EMPTY, Ingredient.EMPTY, Ingredient.EMPTY), 400).withId(id("positioned_slice"));
        var input = new SlicingRecipe.Input(List.of(ingredient.getItems()[0], ItemStack.EMPTY, new ItemStack(Items.GOLD_INGOT),
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
        helper.assertTrue(input.getContainerSize() == 6 && !input.isEmpty() && recipe.matches(input, helper.getLevel()), "Native recipe input contract changed");
        var wrong = new SlicingRecipe.Input(List.of(new ItemStack(Items.GOLD_INGOT), ItemStack.EMPTY, ingredient.getItems()[0],
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
        helper.assertTrue(!recipe.matches(wrong, helper.getLevel()), "Slicer lost positional matching");
        for (Runnable mutation : List.<Runnable>of(() -> input.removeItem(0, 1), () -> input.removeItemNoUpdate(0),
            () -> input.setItem(0, ItemStack.EMPTY), input::clearContent, input::setChanged)) {
            try { mutation.run(); helper.fail("Read-only recipe view accepted a mutation"); } catch (UnsupportedOperationException expected) { }
        }
        helper.assertTrue(input.getItem(0).getCount() == 1, "Rejected mutation lost input");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void actualRecipeJsonRejectsInvalidOutputAndInvalidSlicerShape(GameTestHelper helper) {
        var serializer = new AlloySmeltingRecipe.Serializer();
        var json = serializer.codec().codec().encodeStart(com.mojang.serialization.JsonOps.INSTANCE, alloy()).result().orElseThrow().getAsJsonObject();
        json.getAsJsonObject("output").addProperty("id", "endercore_tests:missing_item");
        try { serializer.fromJson(id("invalid"), json); helper.fail("Unknown recipe output became air"); } catch (com.google.gson.JsonSyntaxException expected) { }
        var slice = JsonParser.parseString("""
            {"output":{"id":"minecraft:diamond","count":1},"inputs":[{"item":"minecraft:stone"}],"energy":400}
            """).getAsJsonObject();
        try { new SlicingRecipe.Serializer().fromJson(id("invalid_slice"), slice); helper.fail("Wrong slicer shape accepted"); }
        catch (com.google.gson.JsonSyntaxException expected) { }
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void weatherRecipeRetainsFluidMatchingAndNativeFireworkData(GameTestHelper helper) {
        var recipe = (com.enderio.enderio.content.machines.obelisks.weather.WeatherChangeRecipe) RecipeManager.fromJson(id("weather"),
            JsonParser.parseString("{\"type\":\"enderio:weather_change\",\"fluid\":{\"id\":\"minecraft:water\",\"amount\":500},\"mode\":\"rain\"}").getAsJsonObject());
        helper.assertTrue(recipe.matches(new com.enderio.enderio.content.machines.obelisks.weather.WeatherChangeRecipe.Input(
            new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 500)), helper.getLevel()), "Weather fluid match changed");
        helper.assertTrue(!recipe.matches(new com.enderio.enderio.content.machines.obelisks.weather.WeatherChangeRecipe.Input(
            new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 499)), helper.getLevel()), "Weather recipe accepted insufficient fluid");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ClientboundUpdateRecipesPacket(List.of(recipe)).write(buffer);
            var received = (com.enderio.enderio.content.machines.obelisks.weather.WeatherChangeRecipe) new ClientboundUpdateRecipesPacket(buffer).getRecipes().get(0);
            helper.assertTrue(received.getId().equals(recipe.getId()) && received.mode() == recipe.mode() && received.fluid().getAmount() == 500, "Weather recipe native sync changed");
        } finally { buffer.release(); }
        var fireworks = recipe.mode().getFireworks();
        var explosions = fireworks.getList("Explosions", net.minecraft.nbt.Tag.TAG_COMPOUND);
        helper.assertTrue(fireworks.getByte("Flight") == 2 && explosions.size() == 1, "Firework flight/explosion count changed");
        var explosion = explosions.getCompound(0);
        helper.assertTrue(explosion.getByte("Type") == net.minecraft.world.item.FireworkRocketItem.Shape.LARGE_BALL.getId()
            && explosion.getIntArray("Colors")[0] == net.minecraft.world.item.DyeColor.BLUE.getFireworkColor()
            && explosion.getIntArray("FadeColors")[0] == net.minecraft.world.item.DyeColor.LIGHT_BLUE.getFireworkColor()
            && explosion.getBoolean("Trail") && !explosion.getBoolean("Flicker"), "Native firework shape/colors/flags changed");
        fireworks.putByte("Flight", (byte) 0);
        helper.assertTrue(recipe.mode().getFireworks().getByte("Flight") == 2, "Firework data aliases enum template");
        helper.succeed();
    }

}
