package com.enderio.enderio.gametests.content;

import com.enderio.enderio.config.base.BaseConfig;
import com.enderio.enderio.content.cold_fire.ColdFireBlock;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOFluids;
import com.enderio.enderio.init.EIOItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.gametests.util.FixtureBuilder;

import java.util.UUID;

// Scenario group: content.fire_crafting
public class FireCraftingTests {

    private static final BlockPos FIRE_POS = new BlockPos(0, 2, 0);
    private static final BlockPos BASE_POS = new BlockPos(0, 1, 0);

    // Test-only infiniburn tags keep these recipe bases lit until the explicit
    // extinguish step. Vanilla random burnout can otherwise precede FIRE_MIN_AGE.
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

    @GameTest(timeoutTicks = 300)
    public static void testColdFireBlockCraftingDrops(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(2, 2, 1)
            .set(0, 0, 0, Blocks.GRANITE.defaultBlockState())
            .set(1, 1, 0, Blocks.OAK_LOG.defaultBlockState()));

        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence()
            .thenExecute(() -> helper.setBlock(FIRE_POS, EIOBlocks.COLD_FIRE.get().defaultBlockState()))
            .thenExecuteAfter(BaseConfig.COMMON.INFINITY.FIRE_MIN_AGE.get() + 1, () -> {
                helper.assertBlockPresent(EIOBlocks.COLD_FIRE.get(), FIRE_POS);
                helper.setBlock(FIRE_POS, Blocks.AIR.defaultBlockState());
            })
            .thenExecute(() -> {
                helper.assertItemEntityPresent(EIOItems.GRAINS_OF_INFINITY.get(), FIRE_POS, 2.0);
                helper.assertBlockPresent(Blocks.GRANITE, BASE_POS);
                helper.assertBlockPresent(Blocks.OAK_LOG, FIRE_POS.east());
            })
            .thenSucceed());
    }

    @GameTest
    public static void igniterOnlySpendsFuelOnSuccessfulPlacement(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(5, 4, 3));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var level = helper.getLevel();
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "IgniterTest"));
            player.setGameMode(GameType.SURVIVAL);
            var held = new ItemStack(EIOItems.COLD_FIRE_IGNITER.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            var tank = held.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(IllegalStateException::new);
            helper.assertTrue(tank.fill(new FluidStack(Fluids.WATER, 19), IFluidHandler.FluidAction.EXECUTE) == 0,
                "only tagged fuel can enter the igniter");
            helper.assertTrue(tank.fill(new FluidStack(EIOFluids.VAPOR_OF_LEVITY.source().get(), 19),
                IFluidHandler.FluidAction.EXECUTE) == 19, "the igniter accepts Vapor of Levity");

            var wall = new BlockPos(1, 2, 2);
            var occupied = new BlockPos(2, 1, 2);
            var base = new BlockPos(3, 1, 0);
            var secondBase = new BlockPos(4, 1, 0);
            helper.setBlock(wall, Blocks.STONE.defaultBlockState());
            helper.setBlock(occupied, Blocks.STONE.defaultBlockState());
            helper.setBlock(occupied.above(), Blocks.OBSIDIAN.defaultBlockState());
            helper.setBlock(base, Blocks.GRANITE.defaultBlockState());
            helper.setBlock(secondBase, Blocks.STONE.defaultBlockState());

            java.util.function.BiFunction<BlockPos, Direction, InteractionResult> use = (relative, face) -> {
                var absolute = helper.absolutePos(relative);
                player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 2.5);
                var hit = new BlockHitResult(Vec3.atCenterOf(absolute).relative(face, 0.5), face, absolute, false);
                return player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND, hit);
            };
            helper.assertTrue(!use.apply(wall, Direction.WEST).consumesAction() && tank.getFluidInTank(0).getAmount() == 19,
                "an unsupported position never spends fuel");
            helper.assertTrue(!use.apply(occupied, Direction.UP).consumesAction() && tank.getFluidInTank(0).getAmount() == 19,
                "an occupied position never spends fuel");
            helper.assertBlockPresent(Blocks.OBSIDIAN, occupied.above());
            helper.assertTrue(use.apply(base, Direction.UP).consumesAction() && tank.getFluidInTank(0).getAmount() == 9,
                "a supported empty position consumes exactly 10 mB");
            helper.assertBlockPresent(EIOBlocks.COLD_FIRE.get(), base.above());
            helper.assertTrue(!use.apply(secondBase, Direction.UP).consumesAction() && tank.getFluidInTank(0).getAmount() == 9,
                "insufficient fuel keeps the remainder intact");
            helper.assertBlockPresent(Blocks.AIR, secondBase.above());
            helper.startSequence().thenSucceed();
        });
    }

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
