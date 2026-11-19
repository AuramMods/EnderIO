package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.api.components.GrindingBallData;
import com.enderio.enderio.content.machines.sag_mill.SagMillingRecipe;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
import com.enderio.enderio.init.EIORecipes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.gametests.util.FixtureBuilder;

import java.util.List;

// Scenario group: machines.sag_mill
public class SagMillTests {

    @GameTest
    public static void grindingPreservesTaggedOutputsAndRespectsStackLimits(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var recipe = new SagMillingRecipe(Ingredient.of(Items.STONE), List.of(
                SagMillingRecipe.OutputItem.of(taggedCobblestone(60, "first"), 1.0f, false),
                SagMillingRecipe.OutputItem.of(taggedCobblestone(7, "first"), 1.0f, false),
                SagMillingRecipe.OutputItem.of(taggedCobblestone(2, "second"), 1.0f, false)), 20,
                SagMillingRecipe.BonusType.NONE);
            var input = new SagMillingRecipe.Input(new ItemStack(Items.STONE), GrindingBallData.IDENTITY);
            var outputs = recipe.craft(input, helper.getLevel().registryAccess());
            helper.assertValueEqual(outputs.size(), 3, "grinding must produce three stacks");
            helper.assertTrue(ItemStack.matches(outputs.get(0).getItem(), taggedCobblestone(64, "first")),
                "the first stack holds exactly its maximum");
            helper.assertTrue(ItemStack.matches(outputs.get(1).getItem(), taggedCobblestone(3, "first")),
                "remaining matching items stay available in the second stack");
            helper.assertTrue(ItemStack.matches(outputs.get(2).getItem(), taggedCobblestone(2, "second")),
                "items with different tags remain separate");
            helper.startSequence().thenSucceed();
        });
    }

    private static ItemStack taggedCobblestone(int count, String variant) {
        var stack = new ItemStack(Items.COBBLESTONE, count);
        stack.getOrCreateTag().putString("Variant", variant);
        return stack;
    }

    @GameTest
    // Tests that the SAG Mill can process stone to cobblestone.
    public static void testSagMillStoneGrinding(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.SAG_MILL.get().defaultBlockState()));

        int energyToAdd = 10000;
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            helper.startSequence()
                .thenExecute(() -> {
                    // Insert the capacitor and fill with energy.
                    helper.insertIntoContainer(0, 1, 0, EIOItems.OCTADIC_CAPACITOR.get(), 1);
                    helper.provideEnergyExact(0, 1, 0, energyToAdd);
                })
                // Insert cobblestone for grinding
                .thenExecute(() -> helper.insertIntoContainer(0, 1, 0, Items.STONE, 2))
                // Wait for processing
                .thenExecuteAfter(40, () -> {
                    // Verify input was consumed
                    helper.assertContainerHasExactly(0, 1, 0, Items.STONE, 0);

                    // Should get gravel output from cobblestone
                    helper.assertContainerHasExactly(0, 1, 0, Items.COBBLESTONE, 2);

                    // Ensure energy was consumed correctly
                    var input = new SagMillingRecipe.Input(new ItemStack(Items.STONE, 1), GrindingBallData.IDENTITY);
                    var recipe = helper.getLevel().getRecipeManager().getRecipeFor(EIORecipes.SAG_MILLING.type().get(), input, helper.getLevel()).orElseThrow();
                    int expectedEnergy = energyToAdd - recipe.getBaseEnergyCost() * 2;
                    helper.assertEnergyStored(0, 1, 0, expectedEnergy);
                })
                .thenSucceed();
        });
    }

    //TODO can we properly handle conditional outputs?
    @GameTest
    // Tests that the SAG Mill can process copper ore to raw copper.
    public static void testSagMillCopperOreGrinding(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.SAG_MILL.get().defaultBlockState()));

        int energyToAdd = 10000;
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            helper.startSequence()
                .thenExecute(() -> {
                    // Insert the capacitor and fill with energy.
                    helper.insertIntoContainer(0, 1, 0, EIOItems.OCTADIC_CAPACITOR.get(), 1);
                    helper.provideEnergyExact(0, 1, 0, energyToAdd);
                })
                // Insert copper ore for grinding
                .thenExecute(() -> helper.insertIntoContainer(0, 1, 0, Items.COPPER_ORE, 2))
                // Wait for processing
                .thenExecuteAfter(40, () -> {
                    // Verify input was consumed
                    helper.assertContainerHasExactly(0, 1, 0, Items.COPPER_ORE, 0);

                    // Should get raw copper output from copper ore
                    helper.assertContainerHasAtleast(0, 1, 0, Items.RAW_COPPER, 2);

                    // Ensure energy was consumed correctly
                    var input = new SagMillingRecipe.Input(new ItemStack(Items.COPPER_ORE, 1), GrindingBallData.IDENTITY);
                    var recipe = helper.getLevel().getRecipeManager().getRecipeFor(EIORecipes.SAG_MILLING.type().get(), input, helper.getLevel()).orElseThrow();
                    int expectedEnergy = energyToAdd - recipe.getBaseEnergyCost() * 2;
                    helper.assertEnergyStored(0, 1, 0, expectedEnergy);
                })
                .thenSucceed();
        });
    }

    @GameTest
    // Tests that the SAG Mill can process raw copper to copper dust.
    public static void testSagMillRawCopperGrinding(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.SAG_MILL.get().defaultBlockState()));

        int energyToAdd = 10000;
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            helper.startSequence()
                .thenExecute(() -> {
                    // Insert the capacitor and fill with energy.
                    helper.insertIntoContainer(0, 1, 0, EIOItems.OCTADIC_CAPACITOR.get(), 1);
                    helper.provideEnergyExact(0, 1, 0, energyToAdd);
                })
                // Insert raw copper for grinding
                .thenExecute(() -> helper.insertIntoContainer(0, 1, 0, Items.RAW_COPPER, 2))
                // Wait for processing
                .thenExecuteAfter(40, () -> {
                    // Verify input was consumed
                    helper.assertContainerHasExactly(0, 1, 0, Items.RAW_COPPER, 0);

                    // Should get powdered copper output from raw copper
                    helper.assertContainerHasAtleast(0, 1, 0, EIOItems.POWDERED_COPPER.asItem(), 2);

                    // Ensure energy was consumed correctly
                    var input = new SagMillingRecipe.Input(new ItemStack(Items.RAW_COPPER, 1), GrindingBallData.IDENTITY);
                    var recipe = helper.getLevel().getRecipeManager().getRecipeFor(EIORecipes.SAG_MILLING.type().get(), input, helper.getLevel()).orElseThrow();
                    int expectedEnergy = energyToAdd - recipe.getBaseEnergyCost() * 2;
                    helper.assertEnergyStored(0, 1, 0, expectedEnergy);
                })
                .thenSucceed();
        });
    }
}

