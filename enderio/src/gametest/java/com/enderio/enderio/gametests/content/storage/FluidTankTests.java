package com.enderio.enderio.gametests.content.storage;

import com.enderio.enderio.gametests.EnderIOTests;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.content.storage.fluid_tank.FluidTankBlockEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.gametests.util.FixtureBuilder;

// Scenario group: content.storage
public class FluidTankTests {
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
                var drained = tank.getFluidHandler().drain(500, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
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
