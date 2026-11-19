package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.content.machines.impulse_hopper.ImpulseHopperBlockEntity;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static com.enderio.enderio.content.machines.impulse_hopper.ImpulseHopperBlockEntity.GHOST;
import static com.enderio.enderio.content.machines.impulse_hopper.ImpulseHopperBlockEntity.INPUT;
import static com.enderio.enderio.content.machines.impulse_hopper.ImpulseHopperBlockEntity.OUTPUT;

public class ImpulseHopperTests {
    @GameTest
    public static void aPartiallyFundedBatchKeepsBothInputsAndCharge(GameplayTest test) {
        template(test);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var hopper = machine(helper, 29);
            configure(hopper, 0, tagged(Items.IRON_INGOT, 2, 137));
            configure(hopper, 1, new ItemStack(Items.REDSTONE));
            helper.startSequence().thenExecuteAfter(45, () -> {
                helper.assertValueEqual(hopper.getEnergyStorage().getEnergyStored(), 29, "a 30 FE batch waits with 29 FE available");
                helper.assertValueEqual(INPUT.get(0).getItemStack(hopper).getCount(), 2, "the first lane keeps its two inputs");
                helper.assertValueEqual(INPUT.get(1).getItemStack(hopper).getCount(), 1, "the second lane keeps its input");
                helper.assertTrue(OUTPUT.get(0).getItemStack(hopper).isEmpty() && OUTPUT.get(1).getItemStack(hopper).isEmpty(),
                    "no part of the batch is delivered");
            }).thenSucceed();
        });
    }

    @GameTest(timeoutTicks = 200)
    public static void aConflictingOutputPausesEveryLaneUntilCleared(GameplayTest test) {
        template(test);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var hopper = machine(helper, 100);
            configure(hopper, 0, tagged(Items.IRON_INGOT, 2, 137));
            configure(hopper, 1, new ItemStack(Items.REDSTONE));
            var wrongTag = tagged(Items.IRON_INGOT, 1, 138);
            OUTPUT.get(0).setStackInSlot(hopper, wrongTag.copy());
            helper.startSequence().thenExecuteAfter(45, () -> {
                helper.assertValueEqual(hopper.getEnergyStorage().getEnergyStored(), 100, "unlike output tags block the whole batch");
                helper.assertTrue(ItemStack.matches(OUTPUT.get(0).getItemStack(hopper), wrongTag), "the existing stack keeps its tag and count");
                helper.assertTrue(OUTPUT.get(1).getItemStack(hopper).isEmpty(), "the other lane waits too");
                OUTPUT.get(0).setStackInSlot(hopper, new ItemStack(Items.COAL));
            }).thenExecuteAfter(45, () -> {
                helper.assertValueEqual(hopper.getEnergyStorage().getEnergyStored(), 100, "a different output item also blocks the whole batch");
                helper.assertValueEqual(INPUT.get(0).getItemStack(hopper).getCount(), 2, "the blocked input is still present");
                OUTPUT.get(0).setStackInSlot(hopper, ItemStack.EMPTY);
            }).thenExecuteAfter(45, () -> {
                helper.assertTrue(ItemStack.matches(OUTPUT.get(0).getItemStack(hopper), tagged(Items.IRON_INGOT, 2, 137)),
                    "the unblocked output preserves the exact input stack");
                helper.assertTrue(OUTPUT.get(1).getItemStack(hopper).is(Items.REDSTONE), "both lanes deliver on the same batch");
                helper.assertValueEqual(hopper.getEnergyStorage().getEnergyStored(), 70, "the resumed batch is billed once at 30 FE");
            }).thenSucceed();
        });
    }

    @GameTest
    public static void clearingAPatternLeavesItsOldInputAvailableWithoutBlockingOtherLanes(GameplayTest test) {
        template(test);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var hopper = machine(helper, 80);
            configure(hopper, 0, tagged(Items.IRON_INGOT, 3, 137));
            configure(hopper, 1, new ItemStack(Items.COAL, 2));
            GHOST.get(1).setStackInSlot(hopper, ItemStack.EMPTY);
            OUTPUT.get(0).setStackInSlot(hopper, tagged(Items.IRON_INGOT, 2, 137));
            helper.startSequence().thenExecuteAfter(45, () -> {
                helper.assertTrue(ItemStack.matches(OUTPUT.get(0).getItemStack(hopper), tagged(Items.IRON_INGOT, 5, 137)),
                    "matching tags merge into five ingots");
                helper.assertValueEqual(hopper.getEnergyStorage().getEnergyStored(), 50, "three delivered items cost 30 FE");
                helper.assertTrue(ItemStack.matches(INPUT.get(1).getItemStack(hopper), new ItemStack(Items.COAL, 2)),
                    "the disabled lane retains its stored input for the player");
                helper.assertTrue(OUTPUT.get(1).getItemStack(hopper).isEmpty(), "the disabled lane produces nothing");
            }).thenSucceed();
        });
    }

    @GameTest(timeoutTicks = 400)
    public static void maximumBatchFitsTheConfiguredDrawAndPaysInFull(GameplayTest test) {
        template(test);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var hopper = machine(helper, 4000);
            for (int lane = 0; lane < 6; lane++) {
                configure(hopper, lane, tagged(Items.IRON_INGOT, 64, 137 + lane));
            }
            helper.startSequence().thenExecuteAfter(260, () -> {
                for (int lane = 0; lane < 6; lane++) {
                    helper.assertTrue(ItemStack.matches(OUTPUT.get(lane).getItemStack(hopper), tagged(Items.IRON_INGOT, 64, 137 + lane)),
                        "lane " + lane + " delivers one full, correctly tagged stack");
                    helper.assertTrue(INPUT.get(lane).getItemStack(hopper).isEmpty(), "lane " + lane + " consumes exactly one stack");
                }
                helper.assertValueEqual(hopper.getEnergyStorage().getEnergyStored(), 160, "384 items cost exactly 3,840 FE");
                helper.assertValueEqual(hopper.ticksForAction(), 240, "at 16 FE per tick a full batch needs a 240-tick interval");
            }).thenSucceed();
        });
    }

    @GameTest
    public static void statusChecksDistinguishConfiguredMissingAndBlockedLanes(GameplayTest test) {
        template(test);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var hopper = machine(helper, 100);
            GHOST.get(0).setStackInSlot(hopper, new ItemStack(Items.COAL, 3));
            helper.assertTrue(hopper.ghostSlotHasItem(0) && !hopper.ghostSlotHasItem(1), "indicators belong to configured lanes");
            helper.assertTrue(!hopper.canPass(0) && hopper.canHoldAndMerge(0), "short input and compatible output are distinct states");
            OUTPUT.get(0).setStackInSlot(hopper, new ItemStack(Items.COAL, 62));
            helper.assertTrue(!hopper.canHoldAndMerge(0), "two free spaces cannot take three coal");
            helper.startSequence().thenSucceed();
        });
    }

    private static void template(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.IMPULSE_HOPPER.get().defaultBlockState()));
    }

    private static ImpulseHopperBlockEntity machine(EnderGameTestHelper helper, int energy) {
        var hopper = helper.getBlockEntity(0, 1, 0, ImpulseHopperBlockEntity.class);
        hopper.getInventory().setStackInSlot(hopper.getCapacitorSlotIndex(), new ItemStack(EIOItems.BASIC_CAPACITOR.get()));
        hopper.getEnergyStorage().setEnergyStored(energy);
        return hopper;
    }

    private static void configure(ImpulseHopperBlockEntity hopper, int lane, ItemStack sample) {
        GHOST.get(lane).setStackInSlot(hopper, sample.copy());
        INPUT.get(lane).setStackInSlot(hopper, sample.copy());
    }

    private static ItemStack tagged(Item item, int count, int label) {
        var stack = new ItemStack(item, count);
        stack.getOrCreateTag().putInt("PortFixture", label);
        return stack;
    }
}
