package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.content.machines.slicer.SlicerBlockEntity;
import com.enderio.enderio.content.machines.slicer.SlicingRecipe;
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

// Scenario group: machines.slicer
public class SlicerTests {

    @GameTest(timeoutTicks = 900)
    public static void paidSlicingWaitsForMissingAxe(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.SLICE_AND_SPLICE.get().defaultBlockState()));

        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var slicer = helper.getBlockEntity(0, 1, 0, SlicerBlockEntity.class);
            var axe = new ItemStack(Items.IRON_AXE);
            var shears = new ItemStack(Items.SHEARS);
            var soularium = EIOItems.SOULARIUM_INGOT.get();
            var silicon = EIOItems.SILICON.get();
            var ingredients = List.of(soularium, Items.ZOMBIE_HEAD, soularium, silicon, Items.REDSTONE, silicon);
            int paid = 3600;
            int remaining = 16400;
            float progress = paid / 20000f;
            helper.startSequence()
                .thenExecute(() -> {
                    slicer.getInventory().setStackInSlot(slicer.getCapacitorSlotIndex(), new ItemStack(EIOItems.BASIC_CAPACITOR.get()));
                    for (int i = 0; i < ingredients.size(); i++) {
                        SlicerBlockEntity.INPUTS.get(i).setStackInSlot(slicer, new ItemStack(ingredients.get(i)));
                    }
                    SlicerBlockEntity.AXE.setStackInSlot(slicer, axe);
                    SlicerBlockEntity.SHEARS.setStackInSlot(slicer, shears);
                    helper.provideEnergyExact(0, 1, 0, paid);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(slicer.getCraftingProgress(), progress, "paid progress"))
                .thenExecute(() -> {
                    SlicerBlockEntity.AXE.setStackInSlot(slicer, ItemStack.EMPTY);
                    helper.provideEnergyExact(0, 1, 0, remaining);
                })
                .thenExecuteAfter(20, () -> {
                    helper.assertValueEqual(slicer.getCraftingProgress(), progress, "progress with the axe removed");
                    helper.assertEnergyStored(0, 1, 0, remaining);
                    helper.assertTrue(!slicer.isActive() && SlicerBlockEntity.OUTPUT.getItemStack(slicer).isEmpty(),
                        "a slicer without an axe cannot process the job");
                    helper.assertValueEqual(shears.getDamageValue(), 0, "shears remain unworn while waiting");
                    SlicerBlockEntity.AXE.setStackInSlot(slicer, axe);
                })
                .thenWaitUntil(() -> helper.assertContainerHasExactly(0, 1, 0, EIOItems.Z_LOGIC_CONTROLLER.get(), 1))
                .thenExecute(() -> {
                    helper.assertEnergyStored(0, 1, 0, 0);
                    helper.assertTrue(SlicerBlockEntity.INPUTS.getItemStacks(slicer.getInventory()).stream().allMatch(ItemStack::isEmpty),
                        "the completed job consumes all six ingredients");
                    helper.assertValueEqual(SlicerBlockEntity.AXE.getItemStack(slicer).getDamageValue(), 1, "axe wear");
                    helper.assertValueEqual(SlicerBlockEntity.SHEARS.getItemStack(slicer).getDamageValue(), 1, "shears wear");
                })
                .thenSucceed();
        });
    }

    @GameTest
    // Tests that the Slice'N'Splice can process a zombie electrode recipe.
    public static void testSlicerRecipe(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.SLICE_AND_SPLICE.get().defaultBlockState()));

        int energyToAdd = 10000;
        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence()
            .thenExecute(() -> {
                // Insert the capacitor and fill with energy.
                helper.insertIntoContainer(0, 1, 0, EIOItems.OCTADIC_CAPACITOR.get(), 1);
                helper.provideEnergyExact(0, 1, 0, energyToAdd);
            })
            // Insert recipe ingredients for Zombie Electrode and axe + shears
            .thenExecute(() -> {
                helper.insertIntoContainer(0, 1, 0, EIOItems.ENERGETIC_ALLOY_INGOT.get(), 2);
                helper.insertIntoContainer(0, 1, 0, Items.ZOMBIE_HEAD, 1);
                helper.insertIntoContainer(0, 1, 0, EIOItems.SILICON.get(), 2);
                helper.insertIntoContainer(0, 1, 0, Items.COBBLESTONE, 1);
                helper.insertIntoContainer(0, 1, 0, Items.DIAMOND_AXE, 1);
                helper.insertIntoContainer(0, 1, 0, Items.SHEARS, 1);
            })
            // Wait for the recipe to process (low energy cost = 100, so should be fast)
            .thenExecuteAfter(20, () -> {
                // Verify inputs have been extracted
                helper.assertContainerHasExactly(0, 1, 0, EIOItems.ENERGETIC_ALLOY_INGOT.get(), 0);
                helper.assertContainerHasExactly(0, 1, 0, Items.ZOMBIE_HEAD, 0);
                helper.assertContainerHasExactly(0, 1, 0, EIOItems.SILICON.get(), 0);
                helper.assertContainerHasExactly(0, 1, 0, Items.COBBLESTONE, 0);

                // Verify that we have 1 Zombie Electrode in the output
                helper.assertContainerHasExactly(0, 1, 0, EIOItems.ZOMBIE_ELECTRODE.get(), 1);

                var input = new SlicingRecipe.Input(List.of(
                    new ItemStack(EIOItems.ENERGETIC_ALLOY_INGOT.get(), 1),
                    new ItemStack(Items.ZOMBIE_HEAD, 1),
                    new ItemStack(EIOItems.ENERGETIC_ALLOY_INGOT.get(), 1),
                    new ItemStack(EIOItems.SILICON.get(), 1),
                    new ItemStack(Items.COBBLESTONE, 1),
                    new ItemStack(EIOItems.SILICON.get(), 1)
                ));

                var recipe = helper.getLevel().getRecipeManager().getRecipeFor(EIORecipes.SLICING.type().get(), input, helper.getLevel()).orElseThrow();
                int expectedEnergy = energyToAdd - recipe.energy();
                helper.assertEnergyStored(0, 1, 0, expectedEnergy);
            })
            .thenSucceed());
    }
}




