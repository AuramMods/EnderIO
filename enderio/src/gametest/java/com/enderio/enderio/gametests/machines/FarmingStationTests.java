package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.api.farm.FarmInteraction;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.content.machines.farming_station.FarmingStationBlockEntity;
import com.enderio.enderio.content.machines.farming_station.tasks.PlantCropFarmTask;
import com.enderio.enderio.foundation.attachment.ActionRange;
import com.enderio.enderio.foundation.souldata.FarmSoul;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;
import java.util.Map;

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

    @GameTest
    public static void savedSoulImmediatelyPreservesFertilizer(GameplayTest test) {
        test.registerGameTestTemplate(() -> fixture(1, 1));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var station = helper.getBlockEntity(0, 1, 0, FarmingStationBlockEntity.class);
            var bee = Soul.of(EntityType.BEE);
            var id = bee.entityTypeId();
            var previous = FarmSoul.FARM.map;
            try {
                FarmSoul.FARM.map = Map.of(id, new FarmSoul.SoulData(id, 0f, 0, 1f));
                station.bindSoul(bee);
                var fertilizer = FarmingStationBlockEntity.BONEMEAL.get(0);
                fertilizer.setStackInSlot(station, new ItemStack(Items.BONE_MEAL, 4));
                var reopened = new FarmingStationBlockEntity(station.getBlockPos(), station.getBlockState());
                reopened.setLevel(helper.getLevel());
                reopened.load(station.saveWithoutMetadata());

                helper.assertTrue(Soul.isSameEntity(reopened.getBoundSoul(), bee), "saved station keeps the bee soul");
                helper.assertTrue(reopened.consumeBonemeal(), "bound fertilizer is available immediately on load");
                helper.assertValueEqual(fertilizer.getItemStack(reopened).getCount(), 4, "restored bee rule preserves fertilizer");
            } finally {
                FarmSoul.FARM.map = previous;
            }
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void liveRulesReplaceAndRemoveTheBoundBonus(GameplayTest test) {
        test.registerGameTestTemplate(() -> fixture(1, 1));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var station = helper.getBlockEntity(0, 1, 0, FarmingStationBlockEntity.class);
            var bee = Soul.of(EntityType.BEE);
            var id = bee.entityTypeId();
            var previous = FarmSoul.FARM.map;
            var conserve = new FarmSoul.SoulData(id, 0f, 0, 1f);
            try {
                FarmSoul.FARM.map = Map.of(id, conserve);
                station.bindSoul(bee);
                var fertilizer = FarmingStationBlockEntity.BONEMEAL.get(0);
                fertilizer.setStackInSlot(station, new ItemStack(Items.BONE_MEAL, 4));
                helper.assertTrue(station.consumeBonemeal(), "the first fertilizer action can proceed");
                helper.assertValueEqual(fertilizer.getItemStack(station).getCount(), 4, "the first rule saves a unit");

                FarmSoul.FARM.map = Map.of(id, new FarmSoul.SoulData(id, 1f, 0, 1f));
                helper.assertTrue(station.consumeBonemeal(), "the updated fertilizer action can proceed");
                helper.assertValueEqual(fertilizer.getItemStack(station).getCount(), 3, "the replacement rule consumes a unit");

                FarmSoul.FARM.map = Map.of(id, conserve);
                station.consumeBonemeal();
                helper.assertValueEqual(fertilizer.getItemStack(station).getCount(), 3, "the conserving rule takes effect again");
                FarmSoul.FARM.map = Map.of();
                station.consumeBonemeal();
                helper.assertValueEqual(fertilizer.getItemStack(station).getCount(), 2, "removing the rule restores default consumption");
                helper.assertTrue(Soul.isSameEntity(station.getBoundSoul(), bee), "removing a rule preserves the stored soul");
            } finally {
                FarmSoul.FARM.map = previous;
            }
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void unwindingTheBindingClearsTheBonus(GameplayTest test) {
        test.registerGameTestTemplate(() -> fixture(1, 1));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var station = helper.getBlockEntity(0, 1, 0, FarmingStationBlockEntity.class);
            station.bindSoul(Soul.of(EntityType.BEE));
            station.bindSoul(Soul.EMPTY);
            helper.assertTrue(station.getBoundSoul().isEmpty(), "an empty vial clears the binding");
            var fertilizer = FarmingStationBlockEntity.BONEMEAL.get(0);
            fertilizer.setStackInSlot(station, new ItemStack(Items.BONE_MEAL, 2));
            helper.assertTrue(station.consumeBonemeal(), "the unbound station can fertilize");
            helper.assertValueEqual(fertilizer.getItemStack(station).getCount(), 1, "the unbound station consumes one unit");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest(timeoutTicks = 400)
    public static void stationCannotHarvestUntilACapacitorIsInstalled(GameplayTest test) {
        var mature = ((CropBlock) Blocks.WHEAT).getStateForAge(7);
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(3, 2, 1)
            .set(0, 1, 0, EIOBlocks.FARMING_STATION.get().defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true))
            .set(2, 1, 0, Blocks.GLOWSTONE.defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var station = helper.getBlockEntity(0, 2, 0, FarmingStationBlockEntity.class);
            var crop = new BlockPos(1, 2, 0);
            helper.setBlock(crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            helper.setBlock(crop, mature);
            station.setActionRange(new ActionRange(1, false));
            var output = FarmingStationBlockEntity.OUTPUT.get(0);
            helper.startSequence()
                .thenExecuteAfter(100, () -> {
                    helper.assertValueEqual(helper.getBlockState(crop), mature, "missing capacitor keeps the crop planted");
                    helper.assertTrue(output.getItemStack(station).isEmpty(), "the station cannot produce a free harvest");
                    helper.insertIntoContainer(0, 2, 0, EIOItems.BASIC_CAPACITOR.get(), 1);
                    station.getEnergyStorage().setEnergyStored(32000);
                })
                .thenWaitUntil(() -> helper.assertTrue(ItemStack.matches(output.getItemStack(station), new ItemStack(Items.WHEAT)),
                    "the very same plot can be harvested with a charged capacitor"))
                .thenSucceed();
        });
    }

    @GameTest(timeoutTicks = 450)
    public static void carriedRangeControlsAnAlreadyLoadedStation(GameplayTest test) {
        restoredRangeControlsTheWorkingPlot(test, true);
    }

    @GameTest(timeoutTicks = 450)
    public static void savedRangeControlsAnAlreadyLoadedStation(GameplayTest test) {
        restoredRangeControlsTheWorkingPlot(test, false);
    }

    private static void restoredRangeControlsTheWorkingPlot(GameplayTest test, boolean carried) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(6, 2, 6)
            .set(3, 1, 3, EIOBlocks.FARMING_STATION.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var station = helper.getBlockEntity(3, 2, 3, FarmingStationBlockEntity.class);
            var inside = new BlockPos(4, 2, 3);
            var outside = new BlockPos(1, 2, 2);
            helper.setBlock(inside.below(), Blocks.DIRT);
            helper.setBlock(outside.below(), Blocks.DIRT);
            helper.setBlock(inside, Blocks.PUMPKIN);
            helper.setBlock(outside, Blocks.PUMPKIN);
            station.setActionRange(new ActionRange(2, false));
            var range = new ActionRange(1, true);
            if (carried) {
                var stack = new ItemStack(EIOBlocks.FARMING_STATION.get());
                EIODataComponents.ACTION_RANGE.set(stack, range);
                station.applyItemData(stack);
            } else {
                var saved = station.saveWithoutMetadata();
                saved.put("ActionRange", range.save(helper.getLevel().registryAccess()));
                station.load(saved);
            }
            helper.assertValueEqual(station.getActionRange(), range, "the new range is present");
            helper.insertIntoContainer(3, 2, 3, EIOItems.BASIC_CAPACITOR.get(), 1);
            station.getEnergyStorage().setEnergyStored(4000);

            helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockPresent(Blocks.AIR, inside))
                .thenExecute(() -> helper.assertBlockPresent(Blocks.PUMPKIN, outside))
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
