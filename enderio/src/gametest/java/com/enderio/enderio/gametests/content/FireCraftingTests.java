package com.enderio.enderio.gametests.content;

import com.enderio.enderio.config.base.BaseConfig;
import com.enderio.enderio.content.cold_fire.ColdFireBlock;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOFluids;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.level.block.Blocks;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.gametests.util.FixtureBuilder;

// Scenario group: content.fire_crafting
public class FireCraftingTests {

    private static final BlockPos FIRE_POS = new BlockPos(0, 2, 0);
    private static final BlockPos BASE_POS = new BlockPos(0, 1, 0);

    @GameTest(timeoutTicks = 300)
    // Tests that fire on a valid base block produces item drops.
    public static void testFireBlockCraftingDrops(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 2, 1)
                .set(0, 0, 0, Blocks.GRANITE.defaultBlockState()));

        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence()
                .thenExecute(() -> helper.setBlock(FIRE_POS, Blocks.FIRE.defaultBlockState()))
                .thenExecuteAfter(BaseConfig.COMMON.INFINITY.FIRE_MIN_AGE.get() + 1, () -> helper.setBlock(FIRE_POS, Blocks.AIR.defaultBlockState()))
                .thenExecute(() -> {
                    helper.assertItemEntityPresent(EIOItems.GRAINS_OF_INFINITY.get(), FIRE_POS, 2.0);
                    helper.assertBlockPresent(Blocks.GRANITE, BASE_POS);
                })
                .thenSucceed());
    }

    // TODO: This test is flakey.
//    @GameTest(timeoutTicks = 300)
//    // Tests that cold fire on a valid base block produces item drops.
//    public static void testColdFireBlockCraftingDrops(final GameplayTest test) {
//        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 2, 1)
//            .set(0, 0, 0, Blocks.GRANITE.defaultBlockState()));
//
//        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence()
//            .thenExecute(() -> helper.setBlock(FIRE_POS, EIOBlocks.COLD_FIRE.get().defaultBlockState()))
//            .thenIdle(280) //TODO figure oout why coldfire needs a bit more ticks?
//            //.thenExecuteAfter(BaseConfig.COMMON.INFINITY.FIRE_MIN_AGE.get() + 1, () -> helper.setBlock(FIRE_POS, Blocks.AIR.defaultBlockState()))
//            .thenExecute(() -> {
//                helper.assertItemEntityPresent(EIOItems.GRAINS_OF_INFINITY.get(), FIRE_POS, 2.0);
//                helper.assertBlockPresent(Blocks.GRANITE, BASE_POS);
//            })
//            .thenSucceed());
//    }

    @GameTest
    // Tests that fire water on a valid base block produces item drops after a random tick.
    public static void testFireWaterCraftingDrops(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 2, 1)
                .set(0, 0, 0, Blocks.GRANITE.defaultBlockState())
                .set(0, 1, 0, EIOFluids.FIRE_WATER.block().get().defaultBlockState()));

        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence()
                .thenExecute(() -> helper.randomTick(FIRE_POS))
                .thenExecute(() -> {
                    helper.assertItemEntityPresent(EIOItems.GRAINS_OF_INFINITY.get(), FIRE_POS, 2.0);
                    helper.assertBlockPresent(Blocks.GRANITE, BASE_POS);
                })
                .thenSucceed());
    }

    @GameTest(timeoutTicks = 300)
    // Tests that fire on a valid base block produces item drops and changes the block below.
    public static void testFireBlockCraftingDropsAndBlockChange(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 2, 1)
                .set(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState()));

        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence()
                .thenExecute(() -> helper.setBlock(FIRE_POS, Blocks.FIRE.defaultBlockState()))
                .thenExecuteAfter(BaseConfig.COMMON.INFINITY.FIRE_MIN_AGE.get() + 1, () -> helper.setBlock(FIRE_POS, Blocks.AIR.defaultBlockState()))
                .thenExecute(() -> {
                    helper.assertItemEntityPresent(EIOItems.GRAINS_OF_INFINITY.get(), FIRE_POS, 2.0);
                    helper.assertBlockPresent(Blocks.STONE, BASE_POS);
                })
                .thenSucceed());
    }

    @GameTest
    // Tests that fire water on a valid base block produces item drops and changes the block below.
    public static void testFireWaterCraftingDropsAndBlockChange(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 2, 1)
                .set(0, 0, 0, Blocks.COBBLESTONE.defaultBlockState())
                .set(0, 1, 0, EIOFluids.FIRE_WATER.block().get().defaultBlockState()));

        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence()
                .thenExecute(() -> helper.randomTick(FIRE_POS))
                .thenExecute(() -> {
                    helper.assertItemEntityPresent(EIOItems.GRAINS_OF_INFINITY.get(), FIRE_POS, 2.0);
                    helper.assertBlockPresent(Blocks.STONE, BASE_POS);
                })
                .thenSucceed());
    }

    @GameTest(timeoutTicks = 310)
    // Tests that cold fire goes out.
    public static void testColdFireAges(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 2, 1)
            .set(0, 0, 0, Blocks.GRANITE.defaultBlockState()));

        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence()
            .thenExecute(() -> helper.setBlock(FIRE_POS,EIOBlocks.COLD_FIRE.get().defaultBlockState().setValue(ColdFireBlock.AGE, 15)))
            .thenIdle(300)
            .thenExecute(() -> helper.assertBlockPresent(Blocks.AIR, FIRE_POS))
            .thenSucceed());
    }
}
