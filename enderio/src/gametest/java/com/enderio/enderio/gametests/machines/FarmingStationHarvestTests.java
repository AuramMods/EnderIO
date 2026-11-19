package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.api.farm.FarmInteraction;
import com.enderio.enderio.content.machines.farming_station.FarmingStationBlockEntity;
import com.enderio.enderio.content.machines.farming_station.tasks.HarvestBlockFarmTask;
import com.enderio.enderio.content.machines.farming_station.tasks.HarvestTreeFarmTask;
import com.enderio.enderio.content.machines.farming_station.tasks.PlantSaplingFarmTask;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;

public class FarmingStationHarvestTests {
    @GameTest
    public static void treeRemainsConnectedWhenTheOutputFills(GameplayTest test) {
        test.registerGameTestTemplate(FarmingStationHarvestTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var station = helper.getBlockEntity(0, 2, 1, FarmingStationBlockEntity.class);
            var root = new BlockPos(1, 2, 1);
            helper.setBlock(root.below(), Blocks.DIRT);
            for (int height = 0; height < 3; height++) {
                helper.setBlock(root.above(height), Blocks.OAK_LOG);
            }
            var soil = helper.absolutePos(root.below());
            var seeds = station.getSeedForPos(soil);
            var axe = new ItemStack(Items.DIAMOND_AXE);
            FarmingStationBlockEntity.TOOLS.get(0).setStackInSlot(station, axe);
            seeds.setStackInSlot(station, new ItemStack(Items.OAK_SAPLING, 2));
            var output = FarmingStationBlockEntity.OUTPUT.get(0);
            fillOutputs(station, new ItemStack(Items.OAK_LOG, 63));

            helper.assertValueEqual(HarvestTreeFarmTask.INSTANCE.process(soil, station), FarmInteraction.BLOCKED,
                "one free output item cannot accommodate the entire trunk");
            helper.assertBlockPresent(Blocks.OAK_LOG, root);
            helper.assertBlockPresent(Blocks.OAK_LOG, root.above());
            helper.assertBlockPresent(Blocks.AIR, root.above(2));
            helper.assertValueEqual(output.getItemStack(station).getCount(), 64, "one top log is collected");
            helper.assertValueEqual(axe.getDamageValue(), 1, "the failed log uses no durability");
            helper.assertTrue(station.getMachineStates().contains(MachineState.FULL_OUTPUT), "the blocked log reports full output");

            output.extractItem(station, 3, false);
            helper.assertValueEqual(HarvestTreeFarmTask.INSTANCE.process(soil, station), FarmInteraction.FINISHED,
                "the station can rediscover the two connected logs");
            helper.assertBlockPresent(Blocks.AIR, root);
            helper.assertBlockPresent(Blocks.AIR, root.above());
            helper.assertValueEqual(output.getItemStack(station).getCount(), 63, "exactly two remaining logs are collected");
            helper.assertValueEqual(axe.getDamageValue(), 3, "each collected log costs one durability");
            helper.assertValueEqual(PlantSaplingFarmTask.INSTANCE.process(soil, station), FarmInteraction.FINISHED,
                "the empty plot can be replanted after the whole harvest");
            helper.assertBlockPresent(Blocks.OAK_SAPLING, root);
            helper.assertValueEqual(seeds.getItemStack(station).getCount(), 1, "one sapling remains reserved");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void branchedTreeCanResumeAfterItsAxeBreaks(GameplayTest test) {
        test.registerGameTestTemplate(FarmingStationHarvestTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var station = helper.getBlockEntity(0, 2, 1, FarmingStationBlockEntity.class);
            var root = new BlockPos(1, 2, 1);
            var elbow = root.above(2).east();
            var tip = elbow.east();
            var leaf = tip.east();
            helper.setBlock(root.below(), Blocks.DIRT);
            for (BlockPos pos : new BlockPos[]{root, root.above(), elbow, tip}) {
                helper.setBlock(pos, Blocks.OAK_LOG);
            }
            helper.setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
            var soil = helper.absolutePos(root.below());
            station.getSeedForPos(soil).setStackInSlot(station, new ItemStack(Items.OAK_SAPLING));
            var axeSlot = FarmingStationBlockEntity.TOOLS.get(0);
            var lastUse = new ItemStack(Items.GOLDEN_AXE);
            lastUse.setDamageValue(lastUse.getMaxDamage() - 1);
            axeSlot.setStackInSlot(station, lastUse);
            var shears = new ItemStack(Items.SHEARS);
            FarmingStationBlockEntity.TOOLS.get(2).setStackInSlot(station, shears);

            helper.assertValueEqual(HarvestTreeFarmTask.INSTANCE.process(soil, station), FarmInteraction.BLOCKED,
                "the broken axe stops the harvest");
            helper.assertTrue(axeSlot.getItemStack(station).isEmpty(), "the spent axe is removed");
            helper.assertBlockPresent(Blocks.AIR, leaf);
            helper.assertBlockPresent(Blocks.AIR, tip);
            helper.assertBlockPresent(Blocks.OAK_LOG, elbow);
            helper.assertBlockPresent(Blocks.OAK_LOG, root.above());
            helper.assertBlockPresent(Blocks.OAK_LOG, root);
            var leaves = FarmingStationBlockEntity.OUTPUT.get(0);
            var logs = FarmingStationBlockEntity.OUTPUT.get(1);
            helper.assertTrue(ItemStack.matches(leaves.getItemStack(station), new ItemStack(Items.OAK_LEAVES)), "shears collect one leaf block");
            helper.assertTrue(ItemStack.matches(logs.getItemStack(station), new ItemStack(Items.OAK_LOG)), "the axe collects one outer log");
            helper.assertValueEqual(shears.getDamageValue(), 1, "shearing uses one durability");

            var replacement = new ItemStack(Items.DIAMOND_AXE);
            axeSlot.setStackInSlot(station, replacement);
            helper.assertValueEqual(HarvestTreeFarmTask.INSTANCE.process(soil, station), FarmInteraction.FINISHED,
                "a replacement axe can reach the entire remainder");
            helper.assertBlockPresent(Blocks.AIR, root);
            helper.assertBlockPresent(Blocks.AIR, root.above());
            helper.assertBlockPresent(Blocks.AIR, elbow);
            helper.assertValueEqual(logs.getItemStack(station).getCount(), 4, "one harvested item per original log");
            helper.assertValueEqual(leaves.getItemStack(station).getCount(), 1, "the leaf was collected once");
            helper.assertValueEqual(replacement.getDamageValue(), 3, "three logs are harvested by the replacement");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void sugarCaneHarvestResumesAboveTheGrowingBase(GameplayTest test) {
        test.registerGameTestTemplate(FarmingStationHarvestTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var station = helper.getBlockEntity(0, 2, 1, FarmingStationBlockEntity.class);
            var base = new BlockPos(1, 2, 1);
            helper.setBlock(base.below().east(), Blocks.WATER);
            helper.setBlock(base.below(), Blocks.SAND);
            for (int height = 0; height < 3; height++) {
                helper.setBlock(base.above(height), Blocks.SUGAR_CANE);
            }
            helper.setBlock(base.above(3), Blocks.GLASS);
            var soil = helper.absolutePos(base.below());
            station.getSeedForPos(soil).setStackInSlot(station, new ItemStack(Items.STICK));
            fillOutputs(station, new ItemStack(Items.SUGAR_CANE, 63));
            var output = FarmingStationBlockEntity.OUTPUT.get(0);
            helper.assertValueEqual(HarvestBlockFarmTask.INSTANCE.process(soil, station), FarmInteraction.BLOCKED,
                "only the top segment fits initially");
            helper.assertBlockPresent(Blocks.SUGAR_CANE, base);
            helper.assertBlockPresent(Blocks.SUGAR_CANE, base.above());
            helper.assertBlockPresent(Blocks.AIR, base.above(2));
            helper.assertValueEqual(output.getItemStack(station).getCount(), 64, "one cane segment is stored");

            output.extractItem(station, 2, false);
            helper.assertValueEqual(HarvestBlockFarmTask.INSTANCE.process(soil, station), FarmInteraction.FINISHED,
                "space allows the second harvest to finish");
            helper.assertBlockPresent(Blocks.SUGAR_CANE, base);
            helper.assertBlockPresent(Blocks.AIR, base.above());
            helper.assertBlockPresent(Blocks.GLASS, base.above(3));
            helper.assertValueEqual(output.getItemStack(station).getCount(), 63, "the middle segment is collected once");
            helper.assertValueEqual(HarvestBlockFarmTask.INSTANCE.process(soil, station), FarmInteraction.IGNORED,
                "the growing base is left alone");
            helper.startSequence().thenSucceed();
        });
    }

    private static FixtureBuilder fixture() {
        return FixtureBuilder.withSize(5, 4, 3).set(0, 1, 1, EIOBlocks.FARMING_STATION.get().defaultBlockState());
    }

    private static void fillOutputs(FarmingStationBlockEntity station, ItemStack first) {
        for (var slot : FarmingStationBlockEntity.OUTPUT.getAccesses()) {
            slot.setStackInSlot(station, new ItemStack(Items.COBBLESTONE, 64));
        }
        FarmingStationBlockEntity.OUTPUT.get(0).setStackInSlot(station, first);
    }
}
