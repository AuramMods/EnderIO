package com.enderio.enderio.gametests.content.storage;

import com.enderio.enderio.gametests.EnderIOTests;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.content.storage.fluid_tank.FluidTankBlockEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.gametests.util.FixtureBuilder;

import java.util.function.Consumer;

// Scenario group: content.storage
public class FluidTankTests {
    @GameTest
    public static void bucketTransferConservesFluidAndContainers(final GameplayTest test) {
        testBothTanks(test, helper -> helper.startSequence().thenExecute(() -> {
            for (int x : new int[] {0, 2}) {
                helper.insertItemIntoSlot(x, 1, 0, FluidTankBlockEntity.FLUID_FILL_INPUT.getIndex(), new ItemStack(Items.WATER_BUCKET));
            }
        }).thenExecuteAfter(30, () -> {
            for (int x : new int[] {0, 2}) {
                helper.assertContainerHasExactly(x, 1, 0, Fluids.WATER, 1000);
                helper.assertSlotHasNoItem(x, 1, 0, FluidTankBlockEntity.FLUID_FILL_INPUT.getIndex());
                helper.assertContainerHasExactly(x, 1, 0, Items.BUCKET, 1);
                var tank = helper.getBlockEntity(x, 1, 0, FluidTankBlockEntity.class);
                FluidTankBlockEntity.FLUID_FILL_OUTPUT.setStackInSlot(tank, ItemStack.EMPTY);
                FluidTankBlockEntity.FLUID_DRAIN_INPUT.setStackInSlot(tank, new ItemStack(Items.BUCKET));
            }
        }).thenExecuteAfter(20, () -> {
            for (int x : new int[] {0, 2}) {
                helper.assertContainerHasExactly(x, 1, 0, Fluids.WATER, 0);
                helper.assertSlotHasNoItem(x, 1, 0, FluidTankBlockEntity.FLUID_DRAIN_INPUT.getIndex());
                helper.assertContainerHasExactly(x, 1, 0, Items.WATER_BUCKET, 1);
            }
        }).thenSucceed());
    }

    @GameTest
    public static void insufficientCapacityPreservesFullBucket(final GameplayTest test) {
        testBothTanks(test, helper -> helper.startSequence().thenExecute(() -> {
            for (int x : new int[] {0, 2}) {
                var tank = helper.getBlockEntity(x, 1, 0, FluidTankBlockEntity.class);
                helper.fillContainer(x, 1, 0, Fluids.WATER, tank.getFluidTank().getCapacity() - 500);
                FluidTankBlockEntity.FLUID_FILL_INPUT.setStackInSlot(tank, new ItemStack(Items.WATER_BUCKET));
            }
        }).thenExecuteAfter(20, () -> {
            for (int x : new int[] {0, 2}) {
                var tank = helper.getBlockEntity(x, 1, 0, FluidTankBlockEntity.class);
                helper.assertContainerHasExactly(x, 1, 0, Fluids.WATER, tank.getFluidTank().getCapacity() - 500);
                helper.assertContainerHasExactly(x, 1, 0, Items.WATER_BUCKET, 1);
                helper.assertSlotHasNoItem(x, 1, 0, FluidTankBlockEntity.FLUID_FILL_OUTPUT.getIndex());
            }
        }).thenSucceed());
    }

    @GameTest
    public static void blockedOutputPreservesFullBucket(final GameplayTest test) {
        testBothTanks(test, helper -> helper.startSequence().thenExecute(() -> {
            for (int x : new int[] {0, 2}) {
                var tank = helper.getBlockEntity(x, 1, 0, FluidTankBlockEntity.class);
                FluidTankBlockEntity.FLUID_FILL_OUTPUT.setStackInSlot(tank, new ItemStack(Items.DIAMOND, 64));
                FluidTankBlockEntity.FLUID_FILL_INPUT.setStackInSlot(tank, new ItemStack(Items.WATER_BUCKET));
            }
        }).thenExecuteAfter(20, () -> {
            for (int x : new int[] {0, 2}) {
                helper.assertContainerHasExactly(x, 1, 0, Fluids.WATER, 0);
                helper.assertContainerHasExactly(x, 1, 0, Items.WATER_BUCKET, 1);
                helper.assertContainerHasExactly(x, 1, 0, Items.DIAMOND, 64);
            }
        }).thenSucceed());
    }

    @GameTest
    public static void partialContainerTransferConservesTaggedFluid(final GameplayTest test) {
        testBothTanks(test, helper -> helper.startSequence().thenExecute(() -> {
            for (int x : new int[] {0, 2}) {
                var tank = helper.getBlockEntity(x, 1, 0, FluidTankBlockEntity.class);
                var contents = new FluidStack(Fluids.WATER, tank.getFluidTank().getCapacity() - 500);
                contents.getOrCreateTag().putInt(EnderIOTests.TEST_NUMBER, 23);
                helper.fillContainer(x, 1, 0, contents);
                var container = new ItemStack(EIOBlocks.FLUID_TANK_ITEM.get());
                var handler = container.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
                    .orElseThrow(() -> new GameTestAssertException("Tank item has no native fluid capability"));
                contents.setAmount(3000);
                if (handler.fill(contents, IFluidHandler.FluidAction.EXECUTE) != 3000) {
                    throw new GameTestAssertException("Could not prepare the actual tank item");
                }
                FluidTankBlockEntity.FLUID_FILL_INPUT.setStackInSlot(tank, handler.getContainer());
            }
        }).thenExecuteAfter(20, () -> {
            for (int x : new int[] {0, 2}) {
                var tank = helper.getBlockEntity(x, 1, 0, FluidTankBlockEntity.class);
                var expected = new FluidStack(Fluids.WATER, tank.getFluidTank().getCapacity());
                expected.getOrCreateTag().putInt(EnderIOTests.TEST_NUMBER, 23);
                helper.assertContainerHasExactly(x, 1, 0, expected);
                var container = FluidTankBlockEntity.FLUID_FILL_INPUT.getItemStack(tank);
                var handler = container.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM)
                    .orElseThrow(() -> new GameTestAssertException("Tank item has no native fluid capability"));
                var remaining = handler.getFluidInTank(0);
                if (remaining.getAmount() != 2500 || !remaining.isFluidEqual(tank.getFluidTank().getFluid())
                    || remaining.getOrCreateTag().getInt(EnderIOTests.TEST_NUMBER) != 23) {
                    throw new GameTestAssertException("Partial transfer changed total fluid amount or native NBT");
                }
                helper.assertSlotHasNoItem(x, 1, 0, FluidTankBlockEntity.FLUID_FILL_OUTPUT.getIndex());
            }
        }).thenSucceed());
    }

    @GameTest
    public static void stackedEmptyBucketsRespectOutputCapacity(final GameplayTest test) {
        testBothTanks(test, helper -> helper.startSequence().thenExecute(() -> {
            for (int x : new int[] {0, 2}) {
                helper.fillContainer(x, 1, 0, Fluids.WATER, 3000);
                helper.insertItemIntoSlot(x, 1, 0, FluidTankBlockEntity.FLUID_DRAIN_INPUT.getIndex(), new ItemStack(Items.BUCKET, 3));
            }
        }).thenExecuteAfter(20, () -> {
            for (int x : new int[] {0, 2}) {
                helper.assertContainerHasExactly(x, 1, 0, Fluids.WATER, 2000);
                helper.assertContainerHasExactly(x, 1, 0, Items.BUCKET, 2);
                helper.assertContainerHasExactly(x, 1, 0, Items.WATER_BUCKET, 1);
            }
        }).thenSucceed());
    }

    private static void testBothTanks(GameplayTest test, Consumer<EnderGameTestHelper> body) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(3, 1, 1)
            .set(0, 0, 0, EIOBlocks.FLUID_TANK.get().defaultBlockState())
            .set(2, 0, 0, EIOBlocks.PRESSURIZED_FLUID_TANK.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, body);
    }

    @GameTest
    // Ensures the fluid tank can store fluids with components
    public static void fluidTankStoresComponents(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.FLUID_TANK.get().defaultBlockState()));

        var fluidToInsert = new FluidStack(Fluids.WATER, 1000);
        fluidToInsert.getOrCreateTag().putInt(EnderIOTests.TEST_NUMBER, 2);

        // Ensure that after insertion, we could still extract the same fluid with components in tact.
        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence()
            .thenExecute(() -> helper.fillContainer(0, 1, 0, fluidToInsert))
            .thenExecute(() -> helper.assertContainerHasExactly(0, 1, 0, fluidToInsert))
            .thenSucceed());
    }

    @GameTest
    public static void standardTankLoadsBeforeLevelAssignment(final GameplayTest test) {
        testSavedTankLoad(test, EIOBlocks.FLUID_TANK.get());
    }

    @GameTest
    public static void pressurizedTankLoadsBeforeLevelAssignment(final GameplayTest test) {
        testSavedTankLoad(test, EIOBlocks.PRESSURIZED_FLUID_TANK.get());
    }

    @GameTest
    public static void legacyTankLayoutsLoadBeforeLevelAssignment(final GameplayTest test) {
        testBothTanks(test, helper -> helper.startSequence().thenExecute(() -> {
            var expected = new FluidStack(Fluids.WATER, 11500);
            expected.getOrCreateTag().putInt(EnderIOTests.TEST_NUMBER, 42);
            for (int x : new int[] {0, 2}) {
                var original = helper.getBlockEntity(x, 1, 0, FluidTankBlockEntity.class);
                var saved = original.saveWithFullMetadata();
                var currentEmpty = saved.getCompound("Fluids");
                saved.remove("Fluids");

                var legacy = expected.writeToNBT(new CompoundTag());
                var entry = legacy.copy();
                entry.putInt("Capacity", original.getFluidTank().getCapacity());
                entry.putInt("Index", 0);
                var overflow = entry.copy();
                overflow.putInt("Index", 3);
                var entries = new ListTag();
                entries.add(entry);
                entries.add(overflow);
                var indexed = new CompoundTag();
                indexed.put("Tanks", entries);
                indexed.putInt("Size", 4);
                saved.put("Fluid", x == 0 ? legacy : indexed);

                var loaded = BlockEntity.loadStatic(original.getBlockPos(), original.getBlockState(), saved);
                if (!(loaded instanceof FluidTankBlockEntity restored) || !expected.isFluidStackIdentical(restored.getFluidTank().getFluid())) {
                    throw new GameTestAssertException("Legacy tank contents, amount or NBT failed native loading");
                }
                var converted = restored.saveWithFullMetadata();
                var convertedEntries = converted.getCompound("Fluids").getList("Tanks", Tag.TAG_COMPOUND);
                if (converted.contains("Fluid") || !convertedEntries.getCompound(0).getCompound("Contents").equals(legacy)) {
                    throw new GameTestAssertException("Migrated fluid was not saved in the native layout");
                }
                if (x == 2 && !convertedEntries.getCompound(1).equals(overflow)) {
                    throw new GameTestAssertException("Migration discarded a tank outside the current machine layout");
                }

                saved.put("Fluids", currentEmpty);
                var nativeLoad = BlockEntity.loadStatic(original.getBlockPos(), original.getBlockState(), saved);
                if (!(nativeLoad instanceof FluidTankBlockEntity nativeTank) || !nativeTank.getFluidTank().getFluid().isEmpty()) {
                    throw new GameTestAssertException("An explicitly empty native tank revived old fluid");
                }
            }
        }).thenSucceed());
    }

    @GameTest
    public static void standardTankResumesSavedRecipe(final GameplayTest test) {
        testSavedRecipe(test, EIOBlocks.FLUID_TANK.get());
    }

    @GameTest
    public static void pressurizedTankResumesSavedRecipe(final GameplayTest test) {
        testSavedRecipe(test, EIOBlocks.PRESSURIZED_FLUID_TANK.get());
    }

    private static void testSavedRecipe(GameplayTest test, Block block) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1).set(0, 0, 0, block.defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence().thenExecute(() -> {
            helper.fillContainer(0, 1, 0, Fluids.WATER, 1000);
            helper.insertItemIntoSlot(0, 1, 0, FluidTankBlockEntity.FLUID_DRAIN_INPUT.getIndex(), new ItemStack(Items.WHITE_CONCRETE_POWDER));
            var original = helper.getBlockEntity(0, 1, 0, FluidTankBlockEntity.class);
            var loaded = BlockEntity.loadStatic(original.getBlockPos(), original.getBlockState(), original.saveWithFullMetadata());
            if (!(loaded instanceof FluidTankBlockEntity restored)) throw new GameTestAssertException("Tank failed native reload");
            original.onChunkUnloaded();
            helper.getLevel().removeBlockEntity(original.getBlockPos());
            helper.getLevel().setBlockEntity(restored);
        }).thenExecuteAfter(10, () -> {
            helper.assertSlotHasNoItem(0, 1, 0, FluidTankBlockEntity.FLUID_DRAIN_INPUT.getIndex());
            helper.assertContainerHasExactly(0, 1, 0, Items.WHITE_CONCRETE, 1);
            helper.assertContainerHasExactly(0, 1, 0, Fluids.WATER, 0);
        }).thenSucceed());
    }

    private static void testSavedTankLoad(GameplayTest test, Block block) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1).set(0, 0, 0, block.defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var contents = new FluidStack(Fluids.WATER, 1500);
            contents.getOrCreateTag().putInt(EnderIOTests.TEST_NUMBER, 23);
            helper.startSequence().thenExecute(() -> {
                var original = helper.getBlockEntity(0, 1, 0, FluidTankBlockEntity.class);
                helper.fillContainer(0, 1, 0, contents);
                original.getInventory().setStackInSlot(FluidTankBlockEntity.FLUID_FILL_OUTPUT.getIndex(), new ItemStack(Items.DIAMOND, 7));
                var saved = original.saveWithFullMetadata();
                // Native chunk loading calls loadStatic before setLevel. A failed load returns null.
                var loaded = BlockEntity.loadStatic(original.getBlockPos(), original.getBlockState(), saved);
                if (!(loaded instanceof FluidTankBlockEntity restored)) {
                    throw new GameTestAssertException("Saved tank was discarded during native load before level assignment");
                }
                if (restored.hasLevel()) throw new GameTestAssertException("Regression fixture unexpectedly already has a level");
                var fluid = restored.getFluidTank().getFluid();
                if (!fluid.isFluidEqual(contents) || fluid.getAmount() != contents.getAmount()) {
                    throw new GameTestAssertException("Tank fluid amount or NBT changed during native loading");
                }
                var output = restored.getInventory().getStackInSlot(FluidTankBlockEntity.FLUID_FILL_OUTPUT.getIndex());
                if (!output.is(Items.DIAMOND) || output.getCount() != 7) {
                    throw new GameTestAssertException("Tank inventory changed during native loading");
                }
                original.onChunkUnloaded();
                helper.getLevel().removeBlockEntity(original.getBlockPos());
                helper.getLevel().setBlockEntity(restored);
            }).thenExecuteAfter(2, () -> {
                helper.assertContainerHasExactly(0, 1, 0, contents);
                var tank = helper.getBlockEntity(0, 1, 0, FluidTankBlockEntity.class);
                var drained = tank.getFluidHandler().drain(500, IFluidHandler.FluidAction.EXECUTE);
                if (drained.getAmount() != 500 || !drained.isFluidEqual(contents)) {
                    throw new GameTestAssertException("Restored tank cannot transfer its saved fluid");
                }
                var remaining = contents.copy();
                remaining.setAmount(1000);
                helper.assertContainerHasExactly(0, 1, 0, remaining);
            }).thenSucceed();
        });
    }
}
