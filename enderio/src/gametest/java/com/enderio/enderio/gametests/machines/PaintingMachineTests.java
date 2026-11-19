package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.config.machines.MachinesConfig;
import com.enderio.enderio.content.machines.painting.PaintingMachineBlockEntity;
import com.enderio.enderio.content.paint.BlockPaintData;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class PaintingMachineTests {
    @GameTest
    public static void loadedNonBlockPaintLeavesTheTemplateAndChargeIntact(GameplayTest test) {
        test.registerGameTestTemplate(PaintingMachineTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var placed = machine(helper);
            var machine = new PaintingMachineBlockEntity(placed.getBlockPos(), placed.getBlockState());
            machine.setLevel(helper.getLevel());
            machine.load(placed.saveWithoutMetadata());
            PaintingMachineBlockEntity.INPUT.setStackInSlot(machine, new ItemStack(Items.OAK_FENCE, 2));
            PaintingMachineBlockEntity.PAINT.setStackInSlot(machine, new ItemStack(Items.STICK));
            int charge = jobCost();
            machine.getEnergyStorage().setEnergyStored(charge);
            machine.load(machine.saveWithoutMetadata());
            machine.serverTick();

            helper.assertValueEqual(machine.getEnergyStorage().getEnergyStored(), charge, "an invalid paint item does not start a job");
            helper.assertValueEqual(PaintingMachineBlockEntity.INPUT.getItemStack(machine).getCount(), 2, "the templates are retained");
            helper.assertTrue(PaintingMachineBlockEntity.PAINT.getItemStack(machine).is(Items.STICK), "the invalid saved item can be recovered");
            helper.assertTrue(PaintingMachineBlockEntity.OUTPUT.getItemStack(machine).isEmpty(), "an invalid paint produces no item");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void removingThePaletteStopsACompletedJobFromPublishing(GameplayTest test) {
        test.registerGameTestTemplate(PaintingMachineTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = machine(helper);
            completeBehindFullOutput(helper, machine);
            var removed = PaintingMachineBlockEntity.PAINT.extractItem(machine, 1, false);
            helper.assertTrue(removed.is(Items.DIAMOND_BLOCK), "the player recovers the reusable palette");
            PaintingMachineBlockEntity.OUTPUT.setStackInSlot(machine, ItemStack.EMPTY);
            machine.serverTick();

            helper.assertTrue(PaintingMachineBlockEntity.OUTPUT.getItemStack(machine).isEmpty(), "a completed job cannot output without paint");
            helper.assertValueEqual(PaintingMachineBlockEntity.INPUT.getItemStack(machine).getCount(), 2, "the template remains when paint is removed");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void aPaidSavedJobUsesTheInstalledPaintWithoutChargingAgain(GameplayTest test) {
        test.registerGameTestTemplate(PaintingMachineTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = machine(helper);
            completeBehindFullOutput(helper, machine);
            PaintingMachineBlockEntity.PAINT.setStackInSlot(machine, new ItemStack(Items.GOLD_BLOCK, 2));
            machine.load(machine.saveWithoutMetadata());
            PaintingMachineBlockEntity.OUTPUT.setStackInSlot(machine, ItemStack.EMPTY);
            machine.serverTick();

            var result = PaintingMachineBlockEntity.OUTPUT.getItemStack(machine);
            helper.assertTrue(result.is(EIOBlocks.PAINTED_FENCE.get().asItem()) && result.getCount() == 1, "one template yields one painted fence");
            helper.assertValueEqual(EIODataComponents.BLOCK_PAINT.get(result), BlockPaintData.of(Blocks.GOLD_BLOCK),
                "the output uses the installed paint after save and reload");
            helper.assertValueEqual(PaintingMachineBlockEntity.INPUT.getItemStack(machine).getCount(), 1, "one template is consumed");
            helper.assertValueEqual(PaintingMachineBlockEntity.PAINT.getItemStack(machine).getCount(), 2, "the palette blocks are reusable");
            helper.assertValueEqual(machine.getEnergyStorage().getEnergyStored(), 0, "the saved job already paid its full cost");
            helper.startSequence().thenSucceed();
        });
    }

    private static FixtureBuilder fixture() {
        return FixtureBuilder.withSize(1, 1, 1).set(0, 0, 0, EIOBlocks.PAINTING_MACHINE.get().defaultBlockState());
    }

    private static PaintingMachineBlockEntity machine(EnderGameTestHelper helper) {
        var machine = helper.getBlockEntity(0, 1, 0, PaintingMachineBlockEntity.class);
        helper.insertIntoContainer(0, 1, 0, EIOItems.BASIC_CAPACITOR.get(), 1);
        return machine;
    }

    private static int jobCost() {
        return MachinesConfig.COMMON.ENERGY.PAINTING_MACHINE_ENERGY_COST.get();
    }

    private static void completeBehindFullOutput(EnderGameTestHelper helper, PaintingMachineBlockEntity machine) {
        var inventory = machine.getInventory();
        PaintingMachineBlockEntity.INPUT.insertItem(inventory, new ItemStack(Items.OAK_FENCE, 2), false);
        PaintingMachineBlockEntity.PAINT.insertItem(inventory, new ItemStack(Items.DIAMOND_BLOCK), false);
        PaintingMachineBlockEntity.OUTPUT.setStackInSlot(machine, new ItemStack(Items.COBBLESTONE, 64));
        int charge = jobCost();
        machine.getEnergyStorage().setEnergyStored(charge);
        for (int tick = 0; tick <= charge / machine.getMaxEnergyUse(); tick++) {
            machine.serverTick();
        }
        helper.assertValueEqual(machine.getEnergyStorage().getEnergyStored(), 0, "the paused job has paid its full energy cost");
        helper.assertTrue(machine.getMachineStates().contains(MachineState.FULL_OUTPUT), "the incompatible output prevents delivery");
        helper.assertValueEqual(PaintingMachineBlockEntity.INPUT.getItemStack(machine).getCount(), 2, "blocked delivery leaves the templates intact");
    }
}
