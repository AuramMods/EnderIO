package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.api.farm.FarmInteraction;
import com.enderio.enderio.content.machines.farming_station.FarmingStationBlockEntity;
import com.enderio.enderio.content.machines.farming_station.tasks.PlantCropFarmTask;
import com.enderio.enderio.foundation.attachment.ActionRange;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;

public class FarmingStationTests {
    @GameTest
    public static void blockedHarvestLeavesTheSeedAndOutputSlotsUntouched(GameplayTest test) {
        test.registerGameTestTemplate(() -> fixture(1, 1));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(0, 1, 0, FarmingStationBlockEntity.class);
            var soil = machine.getBlockPos().east().below();
            var seeds = machine.getSeedForPos(soil);
            seeds.setStackInSlot(machine, new ItemStack(Items.WHEAT_SEEDS, 8));
            fillOutputs(machine);
            var seedDrops = new ItemStack(Items.WHEAT_SEEDS, 3);
            var wheatDrops = new ItemStack(Items.WHEAT, 2);
            var drops = List.of(seedDrops, wheatDrops);
            var before = machine.getInventory().serializeNBT();

            helper.assertTrue(!machine.collectDrops(drops, soil), "harvest waits on a full output");
            helper.assertTrue(machine.getMachineStates().contains(MachineState.FULL_OUTPUT), "full output reported");
            helper.assertValueEqual(machine.getInventory().serializeNBT(), before, "blocked harvest leaves all slots unchanged");
            helper.assertValueEqual(seedDrops.getCount(), 3, "blocked harvest leaves the seed drops unchanged");

            FarmingStationBlockEntity.OUTPUT.get(0).setStackInSlot(machine, ItemStack.EMPTY);
            helper.assertTrue(machine.collectDrops(drops, soil), "same harvest can succeed once space is available");
            helper.assertValueEqual(seeds.getItemStack(machine).getCount(), 11, "three seeds are added once");
            helper.assertValueEqual(FarmingStationBlockEntity.OUTPUT.get(0).getItemStack(machine).getCount(), 2, "both wheat are stored");
            helper.assertTrue(!machine.getMachineStates().contains(MachineState.FULL_OUTPUT), "successful retry clears full output");
            helper.assertValueEqual(wheatDrops.getCount(), 2, "committed harvest leaves the caller's wheat stack unchanged");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void separateDropsShareTheSameRemainingCapacity(GameplayTest test) {
        test.registerGameTestTemplate(() -> fixture(1, 1));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(0, 1, 0, FarmingStationBlockEntity.class);
            fillOutputs(machine);
            var firstSlot = FarmingStationBlockEntity.OUTPUT.get(0);
            firstSlot.setStackInSlot(machine, new ItemStack(Items.WHEAT, 63));
            var drops = List.of(new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT));
            var before = machine.getInventory().serializeNBT();

            helper.assertTrue(!machine.collectDrops(drops, null), "a single free item of capacity cannot accept two drops");
            helper.assertValueEqual(machine.getInventory().serializeNBT(), before, "capacity failure commits no items");

            firstSlot.extractItem(machine, 1, false);
            helper.assertTrue(machine.collectDrops(drops, null), "both drops fit after extracting one existing item");
            helper.assertValueEqual(firstSlot.getItemStack(machine).getCount(), 64, "both drops reach the output");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void seedCollectionPreservesTagsAndStoresEachDropOnce(GameplayTest test) {
        test.registerGameTestTemplate(() -> fixture(1, 1));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(0, 1, 0, FarmingStationBlockEntity.class);
            var soil = machine.getBlockPos().east().below();
            var seedSlot = machine.getSeedForPos(soil);
            var tagged = new ItemStack(Items.WHEAT_SEEDS, 3);
            tagged.getOrCreateTag().putString("FarmFixture", "reserve");
            var other = new ItemStack(Items.WHEAT_SEEDS, 2);
            other.getOrCreateTag().putString("FarmFixture", "output");
            var matching = tagged.copyWithCount(64);

            helper.assertTrue(machine.collectDrops(List.of(tagged, other, matching), soil), "seed reserve and overflow fit");
            helper.assertTrue(ItemStack.matches(seedSlot.getItemStack(machine), tagged.copyWithCount(64)), "reserve holds 64 matching tagged seeds");
            var first = FarmingStationBlockEntity.OUTPUT.get(0).getItemStack(machine);
            var second = FarmingStationBlockEntity.OUTPUT.get(1).getItemStack(machine);
            helper.assertTrue(ItemStack.matches(first, other), "differently tagged seeds retain their tag in the output");
            helper.assertTrue(ItemStack.matches(second, tagged.copyWithCount(3)), "only the three excess matching seeds reach the output");
            helper.assertValueEqual(tagged.getCount(), 3, "neither destination aliases the original seed drop");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest(timeoutTicks = 400)
    public static void poweredStationRetriesMatureWheatThenReplants(GameplayTest test) {
        var mature = ((CropBlock) Blocks.WHEAT).getStateForAge(7);
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(3, 2, 1)
            .set(0, 1, 0, EIOBlocks.FARMING_STATION.get().defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true))
            .set(2, 1, 0, Blocks.GLOWSTONE.defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(0, 2, 0, FarmingStationBlockEntity.class);
            var crop = new BlockPos(1, 2, 0);
            helper.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            helper.setBlock(crop, mature);
            var seedSlot = machine.getSeedForPos(helper.absolutePos(crop.below()));
            var output = FarmingStationBlockEntity.OUTPUT.get(0);
            machine.setActionRange(new ActionRange(1, false));
            helper.insertIntoContainer(0, 2, 0, EIOItems.BASIC_CAPACITOR.get(), 1);
            machine.getEnergyStorage().setEnergyStored(32000);
            seedSlot.setStackInSlot(machine, new ItemStack(Items.WHEAT_SEEDS, 8));
            fillOutputs(machine);
            helper.assertValueEqual(helper.getBlockState(crop), mature, "fixture begins with mature wheat");
            helper.assertValueEqual(PlantCropFarmTask.INSTANCE.process(helper.absolutePos(crop.below()), machine),
                FarmInteraction.IGNORED, "planting yields the occupied plot to the harvest task");

            helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.getMachineStates().contains(MachineState.FULL_OUTPUT), "scheduler attempts a blocked harvest"))
                .thenExecuteAfter(100, () -> {
                    helper.assertValueEqual(helper.getBlockState(crop), mature, "a blocked harvest keeps the mature crop planted");
                    helper.assertValueEqual(seedSlot.getItemStack(machine).getCount(), 8, "repeated blocked harvests cannot add seeds");
                    output.setStackInSlot(machine, ItemStack.EMPTY);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(ItemStack.matches(output.getItemStack(machine), new ItemStack(Items.WHEAT)), "the freed slot receives one wheat");
                    helper.assertTrue(!machine.getMachineStates().contains(MachineState.FULL_OUTPUT), "harvest clears the status");
                })
                .thenWaitUntil(() -> helper.assertValueEqual(helper.getBlockState(crop), ((CropBlock) Blocks.WHEAT).getStateForAge(0), "the station plants a new seed"))
                .thenSucceed();
        });
    }

    private static FixtureBuilder fixture(int width, int height) {
        return FixtureBuilder.withSize(width, height, 1).set(0, 0, 0, EIOBlocks.FARMING_STATION.get().defaultBlockState());
    }

    private static void fillOutputs(FarmingStationBlockEntity machine) {
        for (var output : FarmingStationBlockEntity.OUTPUT.getAccesses()) {
            output.setStackInSlot(machine, new ItemStack(Items.COBBLESTONE, 64));
        }
    }
}
