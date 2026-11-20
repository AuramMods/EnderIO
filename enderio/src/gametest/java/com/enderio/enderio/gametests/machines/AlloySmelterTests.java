package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.content.glass.GlassCollisionPredicate;
import com.enderio.enderio.content.glass.GlassIdentifier;
import com.enderio.enderio.content.glass.GlassLighting;
import com.enderio.enderio.content.machines.alloy.AlloySmelterBlockEntity;
import com.enderio.enderio.content.machines.alloy.AlloySmelterMode;
import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
import com.enderio.enderio.init.EIORecipes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.gametests.util.FixtureBuilder;

import java.util.List;

// Scenario group: machines.alloy_smelter
public class AlloySmelterTests {

    @GameTest
    // Tests that the Alloy Smelter can process a basic alloy recipe.
    public static void testAlloySmelterAlloyingRecipe(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.ALLOY_SMELTER.get().defaultBlockState()));

        int energyToAdd = 10000;
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            helper.startSequence()
                .thenExecute(() -> {
                    // Insert the capacitor and fill with energy.
                    helper.insertIntoContainer(0, 1, 0, EIOItems.OCTADIC_CAPACITOR.get(), 1);
                    helper.provideEnergyExact(0, 1, 0, energyToAdd);
                })
                // Insert recipe ingredients for Dark Steel
                .thenExecute(() -> {
                    helper.insertIntoContainer(0, 1, 0, Items.IRON_INGOT, 1);
                    helper.insertIntoContainer(0, 1, 0, Items.COAL, 2);
                    helper.insertIntoContainer(0, 1, 0, Items.OBSIDIAN, 1);
                })
                // Wait for the recipe to process
                .thenExecuteAfter(20, () -> {
                    // Verify inputs have been extracted
                    helper.assertContainerHasExactly(0, 1, 0, Items.IRON_INGOT, 0);
                    helper.assertContainerHasExactly(0, 1, 0, Items.COAL, 0);
                    helper.assertContainerHasExactly(0, 1, 0, Items.OBSIDIAN, 0);

                    // Verify that we have 2 Dark Steel Ingot in the output
                    helper.assertContainerHasExactly(0, 1, 0, EIOItems.DARK_STEEL_INGOT.get(), 2);

                    // Ensure energy was consumed correctly
                    var input = new AlloySmeltingRecipe.Input(List.of(
                        new ItemStack(Items.IRON_INGOT, 1),
                        new ItemStack(Items.COAL, 2),
                        new ItemStack(Items.OBSIDIAN, 1)
                    ), 1);

                    // Ensure energy was consumed correctly
                    var recipe = helper.getLevel().getRecipeManager().getRecipeFor(EIORecipes.ALLOY_SMELTING.type().get(), input, helper.getLevel()).orElseThrow();
                    int expectedEnergy = energyToAdd - recipe.energy();
                    helper.assertEnergyStored(0, 1, 0, expectedEnergy);
                })
                .thenSucceed();
        });
    }

    @GameTest
    // Tests that the Alloy Smelter can process vanilla smelting recipes (Iron Ingot from ore).
    public static void testAlloySmelterVanillaSmeltingRecipe(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.ALLOY_SMELTER.get().defaultBlockState()));

        int energyToAdd = 10000;
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            helper.startSequence()
                .thenExecute(() -> {
                    // Insert the capacitor and fill with energy.
                    helper.insertIntoContainer(0, 1, 0, EIOItems.OCTADIC_CAPACITOR.get(), 1);
                    helper.provideEnergyExact(0, 1, 0, energyToAdd);
                })
                // Insert raw iron (3 pieces to test batch smelting)
                .thenExecute(() -> helper.insertIntoContainer(0, 1, 0, Items.RAW_IRON, 3))
                // Wait for processing
                .thenExecuteAfter(20, () -> {
                    // Verify input was consumed
                    helper.assertContainerHasExactly(0, 1, 0, Items.RAW_IRON, 0);

                    // Should get 3 iron ingots from 3 raw iron
                    helper.assertContainerHasExactly(0, 1, 0, Items.IRON_INGOT, 3);

                    // Ensure energy was consumed correctly
                    var input = new AlloySmeltingRecipe.Input(List.of(
                        new ItemStack(Items.RAW_IRON, 3),
                        ItemStack.EMPTY,
                        ItemStack.EMPTY
                    ), 3);

                    var recipe = helper.getLevel().getRecipeManager().getRecipeFor(EIORecipes.ALLOY_SMELTING.type().get(), input, helper.getLevel()).orElseThrow();
                    int expectedEnergy = energyToAdd - recipe.energy();
                    helper.assertEnergyStored(0, 1, 0, expectedEnergy);
                })
                .thenSucceed();
        });
    }

    @GameTest
    public static void industrialInsulationUsesStockTagsAndEnergy(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.ALLOY_SMELTER.get().defaultBlockState()));

        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var ingredients = List.of(EIOItems.POWDERED_IRON.get(), Items.ORANGE_WOOL, EIOItems.POWDERED_LAPIS_LAZULI.get());
            helper.startSequence()
                .thenExecute(() -> {
                    helper.getBlockEntity(0, 1, 0, AlloySmelterBlockEntity.class).setMode(AlloySmelterMode.ALLOYS);
                    helper.insertIntoContainer(0, 1, 0, EIOItems.OCTADIC_CAPACITOR.get(), 1);
                    helper.provideEnergyExact(0, 1, 0, 6_500);
                    for (var ingredient : ingredients) helper.insertIntoContainer(0, 1, 0, ingredient, 1);
                })
                .thenWaitUntil(() -> helper.assertContainerHasExactly(0, 1, 0, EIOBlocks.INDUSTRIAL_INSULATION.get().asItem(), 1))
                .thenExecute(() -> {
                    for (var ingredient : ingredients) helper.assertContainerHasExactly(0, 1, 0, ingredient, 0);
                    helper.assertEnergyStored(0, 1, 0, 3_300);
                })
                .thenSucceed();
        });
    }

    @GameTest(timeoutTicks = 120)
    public static void smelterModeSelectsTheMatchingQuartzBlockRecipe(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(3, 1, 1)
            .set(0, 0, 0, EIOBlocks.ALLOY_SMELTER.get().defaultBlockState())
            .set(2, 0, 0, EIOBlocks.ALLOY_SMELTER.get().defaultBlockState()));

        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var quartz = EIOBlocks.GLASS_BLOCKS.get(new GlassIdentifier(GlassLighting.NONE,
                GlassCollisionPredicate.NONE, true)).CLEAR.get().asItem();
            helper.startSequence()
                .thenExecute(() -> {
                    helper.getBlockEntity(0, 1, 0, AlloySmelterBlockEntity.class).setMode(AlloySmelterMode.FURNACE);
                    helper.getBlockEntity(2, 1, 0, AlloySmelterBlockEntity.class).setMode(AlloySmelterMode.ALLOYS);
                    for (int x : new int[]{0, 2}) {
                        helper.insertIntoContainer(x, 1, 0, EIOItems.OCTADIC_CAPACITOR.get(), 1);
                        helper.provideEnergyExact(x, 1, 0, 10_000);
                        helper.insertIntoContainer(x, 1, 0, Items.QUARTZ_BLOCK, 1);
                    }
                })
                .thenWaitUntil(() -> {
                    helper.assertContainerHasExactly(0, 1, 0, Items.SMOOTH_QUARTZ, 1);
                    helper.assertContainerHasExactly(2, 1, 0, quartz, 1);
                })
                .thenExecute(() -> {
                    helper.assertContainerHasExactly(0, 1, 0, Items.QUARTZ_BLOCK, 0);
                    helper.assertContainerHasExactly(2, 1, 0, Items.QUARTZ_BLOCK, 0);
                    helper.assertEnergyStored(0, 1, 0, 8_500);
                    helper.assertEnergyStored(2, 1, 0, 3_600);
                })
                .thenSucceed();
        });
    }
}

