package com.enderio.enderio.gametests.conduits.fluid;

import com.enderio.enderio.api.EnderIORegistries;
import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.content.conduits.type.fluid.FluidConduit;
import com.enderio.enderio.content.conduits.type.fluid.FluidConduitConnectionConfig;
import com.enderio.enderio.content.conduits.type.fluid.FluidConduitTicker;
import com.enderio.enderio.content.conduits.type.redstone.RedstoneConduitConnectionConfig;
import com.enderio.enderio.content.filters.fluid.EnderFluidFilter;
import com.enderio.enderio.content.storage.fluid_tank.FluidTankBlockEntity;
import com.enderio.enderio.gametests.conduits.ConduitGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOConduits;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.List;

public class FluidConduitRateTests {
    @GameTest
    public static void basicConduitTransfersFiveTicksPerUpdate(GameplayTest test) {
        transfer(test, EIOConduits.FLUID, 1000);
    }

    @GameTest
    public static void pressurizedConduitTransfersFiveTicksPerUpdate(GameplayTest test) {
        transfer(test, EIOConduits.ENERGETIC_FLUID, 5000);
    }

    @GameTest
    public static void enderConduitTransfersTheAvailableTankContents(GameplayTest test) {
        // The actual native source tank contains 32,000 mB, below this tier's 40,000 mB update budget.
        transfer(test, EIOConduits.VIBRANT_FLUID, 32000);
    }

    @GameTest
    public static void enderConduitCapsEachNetworkUpdateAtFortyBuckets(GameplayTest test) {
        fullRateRoute(test, EIOConduits.VIBRANT_FLUID, 40_000);
    }

    @GameTest
    public static void pressurizedSegmentCapsAnEnderRouteAtFiveBuckets(GameplayTest test) {
        fullRateRoute(test, EIOConduits.ENERGETIC_FLUID, 5_000);
    }

    private static void fullRateRoute(GameplayTest test, ResourceKey<Conduit<?, ?>> middleTier, int limit) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(5, 1, 1)
            .set(0, 0, 0, Blocks.BARREL.defaultBlockState()).set(4, 0, 0, Blocks.BARREL.defaultBlockState()));
        test.onGameTest(ConduitGameTestHelper.class, helper -> {
            var ender = helper.getConduit(EIOConduits.VIBRANT_FLUID);
            var middle = helper.getConduit(middleTier);
            helper.startSequence().thenExecute(() -> {
                helper.placeConduit(ender, 1, 1, 0);
                helper.placeConduit(middle, 2, 1, 0);
                helper.placeConduit(ender, 3, 1, 0);
                configure(helper, ender, 1, 0, Direction.WEST, true);
                configure(helper, ender, 3, 0, Direction.EAST, false);
            }).thenExecuteAfter(3, () -> {
                var source = (ChangingFluidHandler) helper.getBlockEntity(0, 1, 0, BarrelBlockEntity.class)
                    .getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST).orElseThrow(IllegalStateException::new);
                var target = (ChangingFluidHandler) helper.getBlockEntity(4, 1, 0, BarrelBlockEntity.class)
                    .getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST).orElseThrow(IllegalStateException::new);
                var water = new FluidStack(Fluids.WATER, 64_000);
                water.getOrCreateTag().putInt("PortFluidTier", 242);
                source.setCapacity(64_000);
                source.setFluid(water);
                target.setCapacity(64_000);
                int deposited = 0;
                for (int update = 0; update < 2; update++) {
                    tick(helper, ender, 1, 0);
                    deposited = Math.min(64_000, deposited + limit);
                    helper.assertValueEqual(target.amount(), deposited, "Destination fluid after scheduled update " + update);
                    helper.assertValueEqual(source.amount(), 64_000 - deposited, "Source balance after scheduled update " + update);
                    helper.assertTrue(target.getFluidInTank(0).isFluidEqual(water), "The original fluid and its NBT reach the target");
                }
            }).thenSucceed();
        });
    }

    @GameTest
    public static void largeDatapackRateDoesNotOverflow(GameplayTest test) {
        transfer(test, ResourceKey.create(EnderIORegistries.Keys.CONDUIT,
            ResourceLocation.fromNamespaceAndPath("enderio_tests", "large_fluid_rate")), 32000);
    }

    private static void transfer(GameplayTest test, ResourceKey<Conduit<?, ?>> key, int expected) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(3, 1, 1)
            .set(0, 0, 0, EIOBlocks.PRESSURIZED_FLUID_TANK.get().defaultBlockState())
            .set(2, 0, 0, EIOBlocks.PRESSURIZED_FLUID_TANK.get().defaultBlockState()));
        test.onGameTest(ConduitGameTestHelper.class, helper -> {
            var conduit = helper.getConduit(key);
            helper.startSequence().thenExecute(() -> {
                helper.placeConduit(conduit, 1, 1, 0);
                configure(helper, conduit, 1, 0, Direction.WEST, true);
                configure(helper, conduit, 1, 0, Direction.EAST, false);
            }).thenExecuteAfter(3, () -> {
                helper.fillContainer(0, 1, 0, Fluids.WATER, 32000);
                tick(helper, conduit, 1, 0);
                helper.assertContainerHasExactly(2, 1, 0, Fluids.WATER, expected);
                helper.assertContainerHasExactly(0, 1, 0, Fluids.WATER, 32000 - expected);
            }).thenSucceed();
        });
    }

    @GameTest
    public static void partialFirstBranchCannotExceedExtractionBudget(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(3, 1, 3)
            .set(0, 0, 1, EIOBlocks.PRESSURIZED_FLUID_TANK.get().defaultBlockState())
            .set(2, 0, 1, EIOBlocks.PRESSURIZED_FLUID_TANK.get().defaultBlockState())
            .set(1, 0, 0, EIOBlocks.PRESSURIZED_FLUID_TANK.get().defaultBlockState()));
        test.onGameTest(ConduitGameTestHelper.class, helper -> {
            var conduit = helper.getConduit(EIOConduits.FLUID);
            helper.startSequence().thenExecute(() -> {
                helper.placeConduit(conduit, 1, 1, 1);
                configure(helper, conduit, 1, 1, Direction.WEST, true);
                configure(helper, conduit, 1, 1, Direction.EAST, false);
                configure(helper, conduit, 1, 1, Direction.NORTH, false);
            }).thenExecuteAfter(3, () -> {
                var network = helper.getConduitBundle(1, 1, 1, false).getConduitNode(conduit).getNetwork();
                network.ensureCachesReady();
                var paths = network.insertConnectionsFrom(network.extractConnections(DyeColor.GREEN).iterator().next());
                helper.assertValueEqual(paths.size(), 2, "Real fluid branch count");
                var first = (FluidTankBlockEntity) helper.getLevel().getBlockEntity(paths.get(0).end().connectedBlockPos());
                var second = (FluidTankBlockEntity) helper.getLevel().getBlockEntity(paths.get(1).end().connectedBlockPos());
                first.getFluidTank().fill(new FluidStack(Fluids.WATER, 31900), IFluidHandler.FluidAction.EXECUTE);
                helper.fillContainer(0, 1, 1, Fluids.WATER, 10000);
                tick(helper, conduit, 1, 1);
                helper.assertValueEqual(first.getFluidTank().getFluidAmount(), 32000, "First destination filled");
                helper.assertValueEqual(second.getFluidTank().getFluidAmount(), 900, "Remaining branch budget");
                helper.assertContainerHasExactly(0, 1, 1, Fluids.WATER, 9000);
            }).thenSucceed();
        });
    }

    @GameTest
    public static void extractionFilterChangesApplyToTheNextTransfer(GameplayTest test) {
        filterFixture(test, helper -> {
            var conduit = helper.getConduit(EIOConduits.FLUID);
            helper.startSequence().thenExecute(() -> prepareSimpleNetwork(helper, conduit)).thenExecuteAfter(3, () -> {
                helper.fillContainer(0, 1, 0, Fluids.WATER, 6000);
                var inventory = helper.getConduitBundle(1, 1, 0, false).getConduitNode(conduit).getInventory(Direction.WEST);
                inventory.setStackInSlot(FluidConduit.EXTRACT_FILTER_SLOT, new ItemStack(EIOItems.BASIC_FLUID_FILTER.get()));
                var filter = inventory.getStackInSlot(FluidConduit.EXTRACT_FILTER_SLOT);
                EIODataComponents.FLUID_FILTER.set(filter, new EnderFluidFilter(List.of(new FluidStack(Fluids.LAVA, 1)), false, false));
                tick(helper, conduit, 1, 0);
                helper.assertContainerHasExactly(0, 1, 0, Fluids.WATER, 6000);
                helper.assertContainerHasExactly(2, 1, 0, Fluids.WATER, 0);

                EIODataComponents.FLUID_FILTER.set(filter, new EnderFluidFilter(List.of(new FluidStack(Fluids.WATER, 1)), false, false));
                tick(helper, conduit, 1, 0);
                helper.assertContainerHasExactly(0, 1, 0, Fluids.WATER, 5000);
                helper.assertContainerHasExactly(2, 1, 0, Fluids.WATER, 1000);

                EIODataComponents.FLUID_FILTER.set(filter, new EnderFluidFilter(List.of(new FluidStack(Fluids.WATER, 1)), true, false));
                tick(helper, conduit, 1, 0);
                helper.assertContainerHasExactly(0, 1, 0, Fluids.WATER, 5000);
                helper.assertContainerHasExactly(2, 1, 0, Fluids.WATER, 1000);
            }).thenSucceed();
        });
    }

    @GameTest
    public static void savedNbtInsertionFilterPreservesTaggedFluid(GameplayTest test) {
        filterFixture(test, helper -> {
            var conduit = helper.getConduit(EIOConduits.FLUID);
            helper.startSequence().thenExecute(() -> prepareSimpleNetwork(helper, conduit)).thenExecuteAfter(3, () -> {
                var offered = new FluidStack(Fluids.WATER, 5000);
                offered.getOrCreateTag().putInt("port_test", 7);
                helper.fillContainer(0, 1, 0, offered);
                var inventory = helper.getConduitBundle(1, 1, 0, false).getConduitNode(conduit).getInventory(Direction.EAST);
                inventory.setStackInSlot(FluidConduit.INSERT_FILTER_SLOT, new ItemStack(EIOItems.BASIC_FLUID_FILTER.get()));
                var filter = inventory.getStackInSlot(FluidConduit.INSERT_FILTER_SLOT);
                var match = offered.copy();
                match.getOrCreateTag().putInt("port_test", 9);
                // Existing saved filters may match NBT even though the basic filter UI cannot enable it.
                EIODataComponents.FLUID_FILTER.set(filter, new EnderFluidFilter(List.of(match), false, true));
                tick(helper, conduit, 1, 0);
                helper.assertContainerHasExactly(0, 1, 0, offered);
                helper.assertContainerHasExactly(2, 1, 0, Fluids.WATER, 0);

                EIODataComponents.FLUID_FILTER.set(filter, new EnderFluidFilter(List.of(match), false, false));
                tick(helper, conduit, 1, 0);
                match = offered.copy();
                EIODataComponents.FLUID_FILTER.set(filter, new EnderFluidFilter(List.of(match), false, true));
                tick(helper, conduit, 1, 0);
                EIODataComponents.FLUID_FILTER.set(filter, new EnderFluidFilter(List.of(match), true, true));
                tick(helper, conduit, 1, 0);
                offered.setAmount(3000);
                helper.assertContainerHasExactly(0, 1, 0, offered);
                offered.setAmount(2000);
                helper.assertContainerHasExactly(2, 1, 0, offered);
            }).thenSucceed();
        });
    }

    @GameTest
    public static void channelAndExtractionChangesInvalidateRouting(GameplayTest test) {
        filterFixture(test, helper -> {
            var conduit = helper.getConduit(EIOConduits.FLUID);
            helper.startSequence().thenExecute(() -> prepareSimpleNetwork(helper, conduit)).thenExecuteAfter(3, () -> {
                helper.fillContainer(0, 1, 0, Fluids.WATER, 6000);
                var bundle = helper.getConduitBundle(1, 1, 0, false);
                bundle.setConnectionConfig(conduit, Direction.EAST,
                    new FluidConduitConnectionConfig(true, DyeColor.RED, false, DyeColor.GREEN,
                        RedstoneControl.NEVER_ACTIVE, DyeColor.RED, 0));
                tick(helper, conduit, 1, 0);
                helper.assertContainerHasExactly(0, 1, 0, Fluids.WATER, 6000);
                helper.assertContainerHasExactly(2, 1, 0, Fluids.WATER, 0);
                configure(helper, conduit, 1, 0, Direction.EAST, false);
                tick(helper, conduit, 1, 0);
                helper.assertContainerHasExactly(2, 1, 0, Fluids.WATER, 1000);
                bundle.setConnectionConfig(conduit, Direction.WEST,
                    FluidConduitConnectionConfig.DEFAULT.withIsExtract(true).withIsInsert(false)
                        .withExtractRedstoneControl(RedstoneControl.NEVER_ACTIVE));
                tick(helper, conduit, 1, 0);
                helper.assertContainerHasExactly(0, 1, 0, Fluids.WATER, 5000);
                helper.assertContainerHasExactly(2, 1, 0, Fluids.WATER, 1000);
                configure(helper, conduit, 1, 0, Direction.WEST, true);
                tick(helper, conduit, 1, 0);
                helper.assertContainerHasExactly(0, 1, 0, Fluids.WATER, 4000);
                helper.assertContainerHasExactly(2, 1, 0, Fluids.WATER, 2000);
            }).thenSucceed();
        });
    }

    @GameTest(timeoutTicks = 100)
    public static void remoteBundledSignalUpdatesFluidRoutingWithoutReopeningTheMenu(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(4, 1, 2)
            .set(0, 0, 1, EIOBlocks.PRESSURIZED_FLUID_TANK.get().defaultBlockState())
            .set(3, 0, 1, EIOBlocks.PRESSURIZED_FLUID_TANK.get().defaultBlockState()));
        test.onGameTest(ConduitGameTestHelper.class, helper -> {
            var ender = helper.getConduit(EIOConduits.VIBRANT_FLUID);
            var pressurized = helper.getConduit(EIOConduits.ENERGETIC_FLUID);
            var redstone = helper.getConduit(EIOConduits.REDSTONE);
            var lever = Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR);
            helper.startSequence().thenExecute(() -> {
                helper.setBlock(2, 0, 0, Blocks.STONE);
                helper.setBlock(2, 1, 0, lever);
                helper.placeConduit(ender, 1, 1, 1);
                helper.placeConduit(pressurized, 2, 1, 1);
                var source = helper.getConduitBundle(1, 1, 1, false);
                var target = helper.getConduitBundle(2, 1, 1, false);
                source.addConduit(redstone, null, helper.getPlayer());
                target.addConduit(redstone, null, helper.getPlayer());
                source.setConnectionConfig(ender, Direction.WEST, FluidConduitConnectionConfig.DEFAULT
                    .withExtractRedstoneControl(RedstoneControl.ACTIVE_WITH_SIGNAL).withExtractRedstoneChannel(DyeColor.BLUE));
                configure(helper, pressurized, 2, 1, Direction.EAST, false);
                target.setConnectionConfig(redstone, Direction.NORTH,
                    RedstoneConduitConnectionConfig.DEFAULT.withExtractChannel(DyeColor.BLUE));
                helper.fillContainer(0, 1, 1, Fluids.WATER, 6000);
            }).thenExecuteAfter(12, () -> {
                helper.assertContainerHasExactly(0, 1, 1, Fluids.WATER, 6000);
                helper.assertContainerHasExactly(3, 1, 1, Fluids.WATER, 0);
                helper.setBlock(2, 1, 0, lever.setValue(LeverBlock.POWERED, true));
            }).thenExecuteAfter(18, () -> {
                helper.assertContainerHasExactly(3, 1, 1, Fluids.WATER, 6000);
                helper.setBlock(2, 1, 0, lever);
            }).thenExecuteAfter(4, () -> helper.fillContainer(0, 1, 1, Fluids.WATER, 2000))
                .thenExecuteAfter(12, () -> {
                    helper.assertContainerHasExactly(0, 1, 1, Fluids.WATER, 2000);
                    helper.assertContainerHasExactly(3, 1, 1, Fluids.WATER, 6000);
                }).thenSucceed();
        });
    }

    private static void filterFixture(GameplayTest test, java.util.function.Consumer<ConduitGameTestHelper> body) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(3, 1, 1)
            .set(0, 0, 0, EIOBlocks.PRESSURIZED_FLUID_TANK.get().defaultBlockState())
            .set(2, 0, 0, EIOBlocks.PRESSURIZED_FLUID_TANK.get().defaultBlockState()));
        test.onGameTest(ConduitGameTestHelper.class, body);
    }

    private static void prepareSimpleNetwork(ConduitGameTestHelper helper, Holder<Conduit<?, ?>> conduit) {
        helper.placeConduit(conduit, 1, 1, 0);
        configure(helper, conduit, 1, 0, Direction.WEST, true);
        configure(helper, conduit, 1, 0, Direction.EAST, false);
    }

    @GameTest
    public static void mixedTierPathUsesItsSlowestConduit(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(5, 1, 1)
            .set(0, 0, 0, EIOBlocks.PRESSURIZED_FLUID_TANK.get().defaultBlockState())
            .set(4, 0, 0, EIOBlocks.PRESSURIZED_FLUID_TANK.get().defaultBlockState()));
        test.onGameTest(ConduitGameTestHelper.class, helper -> {
            var fast = helper.getConduit(EIOConduits.VIBRANT_FLUID);
            var slow = helper.getConduit(EIOConduits.FLUID);
            helper.startSequence().thenExecute(() -> {
                helper.placeConduit(fast, 1, 1, 0);
                helper.placeConduit(slow, 2, 1, 0);
                helper.placeConduit(fast, 3, 1, 0);
                configure(helper, fast, 1, 0, Direction.WEST, true);
                configure(helper, fast, 3, 0, Direction.EAST, false);
            }).thenExecuteAfter(3, () -> {
                helper.fillContainer(0, 1, 0, Fluids.WATER, 10000);
                tick(helper, fast, 1, 0);
                helper.assertContainerHasExactly(4, 1, 0, Fluids.WATER, 1000);
                helper.assertContainerHasExactly(0, 1, 0, Fluids.WATER, 9000);
            }).thenSucceed();
        });
    }

    private static void configure(ConduitGameTestHelper helper, Holder<Conduit<?, ?>> conduit,
            int x, int z, Direction side, boolean extract) {
        helper.getConduitBundle(x, 1, z, false).setConnectionConfig(conduit, side,
            FluidConduitConnectionConfig.DEFAULT.withIsExtract(extract).withIsInsert(!extract)
                .withExtractRedstoneControl(RedstoneControl.ALWAYS_ACTIVE));
    }

    private static void tick(ConduitGameTestHelper helper, Holder<Conduit<?, ?>> conduit, int x, int z) {
        var network = helper.getConduitBundle(x, 1, z, false).getConduitNode(conduit).getNetwork();
        network.ensureCachesReady();
        // Invoke one actual scheduled network update in the same server callback as seeding/assertions.
        // Normal world ticks cannot interleave and alter the measured update's budget.
        FluidConduitTicker.INSTANCE.tick(helper.getLevel(), network, (int) Math.floorMod(-helper.getLevel().getGameTime(), 20));
    }
}
