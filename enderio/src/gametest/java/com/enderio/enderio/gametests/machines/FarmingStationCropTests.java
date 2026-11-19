package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.api.farm.FarmInteraction;
import com.enderio.enderio.content.machines.farming_station.FarmingStationBlockEntity;
import com.enderio.enderio.content.machines.farming_station.tasks.BonemealFarmTask;
import com.enderio.enderio.content.machines.farming_station.tasks.HarvestFlower;
import com.enderio.enderio.content.machines.farming_station.tasks.HarvestNetherWartFarmTask;
import com.enderio.enderio.content.machines.farming_station.tasks.HarvestPitcherFarmTask;
import com.enderio.enderio.content.machines.farming_station.tasks.PlantBlockFarmTask;
import com.enderio.enderio.content.machines.farming_station.tasks.PlantCropFarmTask;
import com.enderio.enderio.content.machines.farming_station.tasks.PlantNetherWartFarmTask;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

public class FarmingStationCropTests {
    private static final BlockPos PLOT = new BlockPos(1, 2, 1);

    @GameTest
    public static void pitcherPodGrowsBothHalvesAndWaitsForOutputSpace(GameplayTest test) {
        test.registerGameTestTemplate(FarmingStationCropTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var station = helper.getBlockEntity(0, 2, 1, FarmingStationBlockEntity.class);
            var soil = helper.absolutePos(PLOT.below());
            helper.setBlock(PLOT.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
            var seeds = station.getSeedForPos(soil);
            seeds.setStackInSlot(station, new ItemStack(Items.PITCHER_POD, 2));
            helper.assertValueEqual(PlantCropFarmTask.INSTANCE.process(soil, station), FarmInteraction.FINISHED,
                "the station plants a pitcher pod through the native block item");
            helper.assertValueEqual(helper.getBlockState(PLOT).getValue(PitcherCropBlock.AGE), 0, "pitcher crop begins at age zero");
            helper.assertValueEqual(seeds.getItemStack(station).getCount(), 1, "one pod stays reserved");

            var fertilizer = FarmingStationBlockEntity.BONEMEAL.get(0);
            fertilizer.setStackInSlot(station, new ItemStack(Items.BONE_MEAL, 4));
            for (int age = 1; age <= PitcherCropBlock.MAX_AGE; age++) {
                helper.assertValueEqual(BonemealFarmTask.INSTANCE.process(soil, station), FarmInteraction.FINISHED, "one fertilizer application");
                helper.assertValueEqual(helper.getBlockState(PLOT).getValue(PitcherCropBlock.AGE), age, "pitcher crop advances one age");
            }
            helper.assertTrue(fertilizer.getItemStack(station).isEmpty(), "four ages used four units of fertilizer");
            var lower = helper.getBlockState(PLOT);
            var upper = helper.getBlockState(PLOT.above());
            helper.assertValueEqual(upper.getValue(PitcherCropBlock.HALF), DoubleBlockHalf.UPPER, "the mature crop has an upper half");
            fillOutputs(station);

            helper.assertValueEqual(HarvestPitcherFarmTask.INSTANCE.process(soil, station), FarmInteraction.BLOCKED, "full output pauses pitcher harvest");
            helper.assertValueEqual(helper.getBlockState(PLOT), lower, "the lower half remains intact");
            helper.assertValueEqual(helper.getBlockState(PLOT.above()), upper, "the upper half remains intact");
            var output = FarmingStationBlockEntity.OUTPUT.get(0);
            output.setStackInSlot(station, ItemStack.EMPTY);
            helper.assertValueEqual(HarvestPitcherFarmTask.INSTANCE.process(soil, station), FarmInteraction.FINISHED, "free space accepts the flower");
            helper.assertTrue(ItemStack.matches(output.getItemStack(station), new ItemStack(Items.PITCHER_PLANT)), "both halves yield one flower");
            helper.assertBlockPresent(Blocks.AIR, PLOT);
            helper.assertBlockPresent(Blocks.AIR, PLOT.above());
            helper.assertValueEqual(seeds.getItemStack(station).getCount(), 1, "the reserve stays intact");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void loadedPitcherHarvestProducesOnlyOneFlower(GameplayTest test) {
        test.registerGameTestTemplate(FarmingStationCropTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var station = helper.getBlockEntity(0, 2, 1, FarmingStationBlockEntity.class);
            var soil = helper.absolutePos(PLOT.below());
            var lowerPos = helper.absolutePos(PLOT);
            helper.setBlock(PLOT.below(), Blocks.FARMLAND);
            var lower = Blocks.PITCHER_CROP.defaultBlockState().setValue(PitcherCropBlock.AGE, PitcherCropBlock.MAX_AGE);
            helper.getLevel().setBlock(lowerPos, lower, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            helper.setBlock(PLOT.above(), lower.setValue(PitcherCropBlock.HALF, DoubleBlockHalf.UPPER));
            station.getSeedForPos(soil).setStackInSlot(station, new ItemStack(Items.PITCHER_POD));

            helper.assertValueEqual(HarvestPitcherFarmTask.INSTANCE.process(soil, station), FarmInteraction.FINISHED, "a complete mature crop is harvested");
            helper.assertBlockPresent(Blocks.AIR, PLOT);
            helper.assertBlockPresent(Blocks.AIR, PLOT.above());
            var stored = FarmingStationBlockEntity.OUTPUT.get(0).getItemStack(station);
            helper.assertTrue(ItemStack.matches(stored, new ItemStack(Items.PITCHER_PLANT)), "the inventory receives one mature flower");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(lowerPos).inflate(2),
                item -> item.getItem().is(Items.PITCHER_PLANT)).isEmpty(), "removing the second half cannot also drop a loose flower");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void pitcherHarvestLeavesAnUnrelatedBlockAboveTheCrop(GameplayTest test) {
        test.registerGameTestTemplate(FarmingStationCropTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var station = helper.getBlockEntity(0, 2, 1, FarmingStationBlockEntity.class);
            var soil = helper.absolutePos(PLOT.below());
            helper.setBlock(PLOT.below(), Blocks.FARMLAND);
            helper.setBlock(PLOT.above(), Blocks.GLASS);
            var lower = Blocks.PITCHER_CROP.defaultBlockState().setValue(PitcherCropBlock.AGE, PitcherCropBlock.MAX_AGE);
            helper.getLevel().setBlock(helper.absolutePos(PLOT), lower, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            helper.assertValueEqual(HarvestPitcherFarmTask.INSTANCE.process(soil, station), FarmInteraction.IGNORED,
                "a lower half without its crop partner is left to vanilla block updates");
            helper.assertBlockPresent(Blocks.GLASS, PLOT.above());
            helper.assertTrue(FarmingStationBlockEntity.OUTPUT.getItemStacks(station.getInventory()).stream().allMatch(ItemStack::isEmpty),
                "an invalid crop produces no loot");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void hoeTillsDirtBeforePlantingAndUsesItsLastDurability(GameplayTest test) {
        test.registerGameTestTemplate(FarmingStationCropTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var station = helper.getBlockEntity(0, 2, 1, FarmingStationBlockEntity.class);
            var soil = helper.absolutePos(PLOT.below());
            helper.setBlock(PLOT.below(), Blocks.DIRT);
            var seeds = station.getSeedForPos(soil);
            seeds.setStackInSlot(station, new ItemStack(Items.WHEAT_SEEDS, 2));
            helper.assertValueEqual(PlantCropFarmTask.INSTANCE.process(soil, station), FarmInteraction.BLOCKED, "untouched dirt needs a hoe");
            helper.assertBlockPresent(Blocks.DIRT, PLOT.below());
            var hoe = new ItemStack(Items.IRON_HOE);
            hoe.setDamageValue(hoe.getMaxDamage() - 1);
            var tool = FarmingStationBlockEntity.TOOLS.get(1);
            tool.setStackInSlot(station, hoe);

            helper.assertValueEqual(PlantCropFarmTask.INSTANCE.process(soil, station), FarmInteraction.FINISHED, "the hoe tills the dirt");
            helper.assertBlockPresent(Blocks.FARMLAND, PLOT.below());
            helper.assertTrue(tool.getItemStack(station).isEmpty(), "the last hoe use consumes the tool");
            helper.assertValueEqual(seeds.getItemStack(station).getCount(), 2, "tilling alone uses no seeds");
            helper.assertValueEqual(PlantCropFarmTask.INSTANCE.process(soil, station), FarmInteraction.FINISHED, "the tilled plot is ready for a seed");
            helper.assertBlockPresent(Blocks.WHEAT, PLOT);
            helper.assertValueEqual(seeds.getItemStack(station).getCount(), 1, "planting leaves one seed reserved");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void torchflowerGrowsAndWaitsForRoomBeforeHarvest(GameplayTest test) {
        test.registerGameTestTemplate(FarmingStationCropTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var station = helper.getBlockEntity(0, 2, 1, FarmingStationBlockEntity.class);
            var soil = helper.absolutePos(PLOT.below());
            helper.setBlock(PLOT.below(), Blocks.FARMLAND);
            var seeds = station.getSeedForPos(soil);
            seeds.setStackInSlot(station, new ItemStack(Items.TORCHFLOWER_SEEDS, 2));
            helper.assertValueEqual(PlantCropFarmTask.INSTANCE.process(soil, station), FarmInteraction.FINISHED, "a torchflower seed is planted");
            helper.assertBlockPresent(Blocks.TORCHFLOWER_CROP, PLOT);
            FarmingStationBlockEntity.BONEMEAL.get(0).setStackInSlot(station, new ItemStack(Items.BONE_MEAL, 2));
            for (int i = 0; i < 2; i++) {
                helper.assertValueEqual(BonemealFarmTask.INSTANCE.process(soil, station), FarmInteraction.FINISHED, "torchflower fertilization");
            }
            helper.assertBlockPresent(Blocks.TORCHFLOWER, PLOT);
            fillOutputs(station);
            helper.assertValueEqual(HarvestFlower.INSTANCE.process(soil, station), FarmInteraction.BLOCKED, "the full output keeps the flower planted");
            helper.assertBlockPresent(Blocks.TORCHFLOWER, PLOT);
            var output = FarmingStationBlockEntity.OUTPUT.get(0);
            output.setStackInSlot(station, ItemStack.EMPTY);
            helper.assertValueEqual(HarvestFlower.INSTANCE.process(soil, station), FarmInteraction.FINISHED, "the flower can now be collected");
            helper.assertTrue(ItemStack.matches(output.getItemStack(station), new ItemStack(Items.TORCHFLOWER)), "one flower reaches the output");
            helper.assertValueEqual(seeds.getItemStack(station).getCount(), 1, "the reserve stays intact");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void fullyStockedNetherWartResumesAndReplants(GameplayTest test) {
        test.registerGameTestTemplate(FarmingStationCropTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var station = helper.getBlockEntity(0, 2, 1, FarmingStationBlockEntity.class);
            var soil = helper.absolutePos(PLOT.below());
            helper.setBlock(PLOT.below(), Blocks.SOUL_SAND);
            var reserve = station.getSeedForPos(soil);
            reserve.setStackInSlot(station, new ItemStack(Items.NETHER_WART, 64));
            helper.assertValueEqual(PlantNetherWartFarmTask.INSTANCE.process(soil, station), FarmInteraction.FINISHED, "wart is planted on soul sand");
            var immature = helper.getBlockState(PLOT);
            helper.assertValueEqual(immature.getValue(NetherWartBlock.AGE), 0, "the new wart begins at age zero");
            helper.assertValueEqual(HarvestNetherWartFarmTask.INSTANCE.process(soil, station), FarmInteraction.IGNORED, "immature wart stays planted");
            var mature = immature.setValue(NetherWartBlock.AGE, 3);
            helper.setBlock(PLOT, mature);
            reserve.setStackInSlot(station, new ItemStack(Items.NETHER_WART, 64));
            fillOutputs(station);
            helper.assertValueEqual(HarvestNetherWartFarmTask.INSTANCE.process(soil, station), FarmInteraction.BLOCKED, "a full station keeps the mature wart");
            helper.assertValueEqual(helper.getBlockState(PLOT), mature, "the plant is still mature after backpressure");

            var output = FarmingStationBlockEntity.OUTPUT.get(0);
            output.setStackInSlot(station, ItemStack.EMPTY);
            helper.assertValueEqual(HarvestNetherWartFarmTask.INSTANCE.process(soil, station), FarmInteraction.FINISHED, "room permits a single mature harvest");
            ItemStack harvested = output.getItemStack(station);
            helper.assertTrue(harvested.is(Items.NETHER_WART) && harvested.getCount() >= 2 && harvested.getCount() <= 4,
                "the output contains one vanilla mature wart yield");
            helper.assertValueEqual(PlantNetherWartFarmTask.INSTANCE.process(soil, station), FarmInteraction.FINISHED, "the reserve can replant wart");
            helper.assertValueEqual(helper.getBlockState(PLOT), immature, "replanted wart starts at age zero");
            helper.assertValueEqual(reserve.getItemStack(station).getCount(), 63, "one reserved wart is replanted");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void nativePlantItemsStartCaneCactusAndBamboo(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(7, 3, 7)
            .set(3, 1, 3, EIOBlocks.FARMING_STATION.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var station = helper.getBlockEntity(3, 2, 3, FarmingStationBlockEntity.class);
            var cane = new BlockPos(4, 2, 3);
            var cactus = new BlockPos(4, 2, 5);
            var bamboo = new BlockPos(1, 2, 5);
            helper.setBlock(cane.below().east(), Blocks.WATER);
            helper.setBlock(cane.below(), Blocks.SAND);
            helper.setBlock(cactus.below(), Blocks.SAND);
            helper.setBlock(bamboo.below(), Blocks.DIRT);
            var positions = new BlockPos[]{cane, cactus, bamboo};
            var items = new Item[]{Items.SUGAR_CANE, Items.CACTUS, Items.BAMBOO};
            var plants = new Block[]{Blocks.SUGAR_CANE, Blocks.CACTUS, Blocks.BAMBOO_SAPLING};
            for (int i = 0; i < positions.length; i++) {
                var soil = helper.absolutePos(positions[i].below());
                var reserve = station.getSeedForPos(soil);
                reserve.setStackInSlot(station, new ItemStack(items[i], 2));
                helper.assertValueEqual(PlantBlockFarmTask.INSTANCE.process(soil, station), FarmInteraction.FINISHED, "native planting of " + items[i]);
                helper.assertBlockPresent(plants[i], positions[i]);
                helper.assertValueEqual(reserve.getItemStack(station).getCount(), 1, "one item stays reserved");
            }
            helper.startSequence().thenSucceed();
        });
    }

    private static FixtureBuilder fixture() {
        return FixtureBuilder.withSize(4, 3, 3)
            .set(0, 1, 1, EIOBlocks.FARMING_STATION.get().defaultBlockState())
            .set(2, 1, 1, Blocks.GLOWSTONE.defaultBlockState());
    }

    private static void fillOutputs(FarmingStationBlockEntity station) {
        for (var slot : FarmingStationBlockEntity.OUTPUT.getAccesses()) {
            slot.setStackInSlot(station, new ItemStack(Items.COBBLESTONE, 64));
        }
    }
}
