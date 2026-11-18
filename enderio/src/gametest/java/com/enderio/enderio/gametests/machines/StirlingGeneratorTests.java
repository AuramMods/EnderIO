package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.content.machines.stirling_generator.StirlingGeneratorBlockEntity;
import com.enderio.enderio.foundation.MachineNBTKeys;
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

public class StirlingGeneratorTests {
    @GameTest(timeoutTicks = 200)
    public static void fuelWaitsForCapacitorAndRedstoneThenBurnsExactlyOnce(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(2, 1, 1)
            .set(0, 0, 0, EIOBlocks.STIRLING_GENERATOR.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(0, 1, 0, StirlingGeneratorBlockEntity.class);
            var storage = machine.getEnergyStorage();
            var inventory = machine.getInventory();
            var switchPos = new BlockPos(1, 1, 0);
            helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(StirlingGeneratorBlockEntity.FUEL.insertItem(inventory, new ItemStack(Items.LAVA_BUCKET), true)
                        .is(Items.LAVA_BUCKET), "fuel with a container remainder is rejected");
                    helper.assertTrue(StirlingGeneratorBlockEntity.FUEL.insertItem(inventory, new ItemStack(Items.DIAMOND), true)
                        .is(Items.DIAMOND), "non-fuel is rejected");
                    helper.insertIntoContainer(0, 1, 0, Items.STICK, 2);
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(!machine.isGenerating(), "generator waits for a capacitor");
                    helper.assertContainerHasExactly(0, 1, 0, Items.STICK, 2);
                    machine.setRedstoneControl(RedstoneControl.ACTIVE_WITH_SIGNAL);
                    helper.insertIntoContainer(0, 1, 0, EIOItems.BASIC_CAPACITOR.get(), 1);
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(!machine.isGenerating(), "generator waits for a signal");
                    helper.assertContainerHasExactly(0, 1, 0, Items.STICK, 2);
                    helper.setBlock(switchPos, Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(machine.isGenerating(), "signal ignites one stick"))
                .thenExecute(() -> {
                    helper.assertValueEqual(machine.saveWithoutMetadata().getInt(MachineNBTKeys.BURN_DURATION), 37, "stick burn duration");
                    helper.assertContainerHasExactly(0, 1, 0, Items.STICK, 1);
                    helper.setBlock(switchPos, Blocks.AIR);
                })
                .thenWaitUntil(() -> helper.assertTrue(!machine.isGenerating(), "lit fuel finishes without a signal"))
                .thenExecute(() -> {
                    helper.assertValueEqual(storage.getEnergyStored(), 1480, "energy of first stick");
                    helper.assertContainerHasExactly(0, 1, 0, Items.STICK, 1);
                    helper.setBlock(switchPos, Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(machine.getMachineStates().contains(MachineState.EMPTY_INPUT), "fuel slot runs dry"))
                .thenExecute(() -> {
                    helper.assertValueEqual(storage.getEnergyStored(), 2960, "total energy of two sticks");
                    helper.assertContainerHasExactly(0, 1, 0, Items.STICK, 0);
                })
                .thenSucceed();
        });
    }

    @GameTest
    public static void fullPowerStatusFollowsTheLiveBuffer(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.STIRLING_GENERATOR.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(0, 1, 0, StirlingGeneratorBlockEntity.class);
            var storage = machine.getEnergyStorage();
            helper.startSequence()
                .thenExecute(() -> {
                    helper.insertIntoContainer(0, 1, 0, EIOItems.BASIC_CAPACITOR.get(), 1);
                    storage.setEnergyStored(storage.getMaxEnergyStored() - 1);
                    helper.insertIntoContainer(0, 1, 0, Items.COAL, 2);
                })
                .thenWaitUntil(() -> helper.assertTrue(machine.getMachineStates().contains(MachineState.FULL_POWER), "full power status set"))
                .thenExecute(() -> {
                    helper.assertValueEqual(storage.getEnergyStored(), 64000, "exact buffer capacity");
                    helper.assertContainerHasExactly(0, 1, 0, Items.COAL, 1);
                    storage.extractEnergy(1, false);
                    helper.assertTrue(!machine.getMachineStates().contains(MachineState.FULL_POWER), "full power status clears after extraction");
                })
                .thenSucceed();
        });
    }
}
