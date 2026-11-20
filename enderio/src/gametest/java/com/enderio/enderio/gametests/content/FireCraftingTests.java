package com.enderio.enderio.gametests.content;

import com.enderio.core.CoreNBTKeys;
import com.enderio.enderio.config.base.BaseConfig;
import com.enderio.enderio.content.cold_fire.ColdFireBlock;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOFluids;
import com.enderio.enderio.init.EIOItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
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
    private static final BlockPos VANILLA_FIRE_POS = new BlockPos(1, 2, 1);

    // Test-only infiniburn tags keep these recipe bases lit until the explicit
    // extinguish step. Vanilla random burnout can otherwise precede FIRE_MIN_AGE.
    @GameTest(timeoutTicks = 300)
    // Tests that fire on a valid base block produces item drops.
    public static void testFireBlockCraftingDrops(final GameplayTest test) {
        checkMatureVanillaFireDrops(test, Blocks.GRANITE, Blocks.GRANITE);
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
    public static void forge6IgniterRetainsTaggedFuel(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var fixture = new ItemStack(EIOItems.COLD_FIRE_IGNITER.get());
            fixture.setHoverName(Component.literal("Archive Igniter"));
            fixture.getOrCreateTag().putInt("Marker", 181);
            var oldFuel = new CompoundTag();
            oldFuel.putString("FluidName", "enderio:vapor_of_levity");
            oldFuel.putInt("Amount", 25);
            var marker = new CompoundTag();
            marker.putInt("Batch", 27);
            oldFuel.put("Tag", marker);
            fixture.addTagElement(CoreNBTKeys.FLUID, oldFuel);

            var converted = ItemStack.of(fixture.save(new CompoundTag()));
            var vapor = EIOFluids.VAPOR_OF_LEVITY.source().get();
            var contents = converted.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
                .orElseThrow(IllegalStateException::new).getFluidInTank(0);
            helper.assertTrue(contents.getFluid() == vapor && contents.getAmount() == 25 && marker.equals(contents.getTag()),
                "Forge load keeps the exact original fuel and its tag");
            helper.assertTrue(!converted.getTag().contains(CoreNBTKeys.FLUID) && converted.getTag().getInt("Marker") == 181
                && converted.getHoverName().getString().equals("Archive Igniter"), "only the migrated tag should change");

            EIODataComponents.ITEM_FLUID_CONTENT.set(converted, new FluidStack(vapor, 11, marker));
            converted.addTagElement(CoreNBTKeys.FLUID, oldFuel.copy());
            var reopened = ItemStack.of(converted.save(new CompoundTag()));
            helper.assertTrue(EIODataComponents.ITEM_FLUID_CONTENT.get(reopened).getAmount() == 11
                && !reopened.getTag().contains(CoreNBTKeys.FLUID), "current item data takes precedence over leftover legacy data");

            var unavailable = oldFuel.copy();
            unavailable.putString("FluidName", "example:removed_vapor");
            fixture.addTagElement(CoreNBTKeys.FLUID, unavailable);
            var preserved = ItemStack.of(fixture.save(new CompoundTag()));
            helper.assertTrue(!EIODataComponents.ITEM_FLUID_CONTENT.has(preserved)
                && unavailable.equals(preserved.getTagElement(CoreNBTKeys.FLUID)),
                "unavailable fluid data stays on the item for a later load");
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
        checkMatureVanillaFireDrops(test, Blocks.COBBLESTONE, Blocks.STONE);
    }

    private static void checkMatureVanillaFireDrops(GameplayTest test, Block base, Block result) {
        test.registerGameTestTemplate(() -> {
            var fixture = FixtureBuilder.withSize(3, 3, 3);
            var stone = Blocks.STONE.defaultBlockState();
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    fixture.set(x, 0, z, stone);
                    fixture.set(x, 2, z, stone);
                }
            }
            for (Direction side : Direction.Plane.HORIZONTAL) {
                fixture.set(1 + side.getStepX(), 1, 1 + side.getStepZ(), stone);
            }
            return fixture.set(1, 0, 1, base.defaultBlockState());
        });

        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence()
            .thenExecute(() -> helper.setBlock(VANILLA_FIRE_POS, Blocks.FIRE))
            .thenExecuteAfter(BaseConfig.COMMON.INFINITY.FIRE_MIN_AGE.get() + 1, () -> {
                helper.assertBlockPresent(Blocks.FIRE, VANILLA_FIRE_POS);
                helper.assertBlockPresent(base, VANILLA_FIRE_POS.below());
                helper.setBlock(VANILLA_FIRE_POS, Blocks.AIR);
            })
            .thenExecute(() -> {
                helper.assertItemEntityPresent(EIOItems.GRAINS_OF_INFINITY.get(), VANILLA_FIRE_POS, 0.5);
                helper.assertBlockPresent(result, VANILLA_FIRE_POS.below());
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
