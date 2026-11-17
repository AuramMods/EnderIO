package com.enderio.core.gametest;

import com.enderio.core.CoreNBTKeys;
import com.enderio.core.common.recipes.OutputStack;
import com.enderio.core.common.serialization.StackDataCodecs;
import com.enderio.enderio.api.capacitor.CapacitorData;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.io.energy.EnergyIOMode;
import com.enderio.enderio.foundation.inventory.MachineInventory;
import com.enderio.enderio.foundation.inventory.MachineInventoryLayout;
import com.enderio.enderio.foundation.io.energy.MachineEnergyStorage;
import com.enderio.enderio.foundation.io.fluid.MachineFluidHandler;
import com.enderio.enderio.foundation.io.fluid.MachineTankLayout;
import com.enderio.enderio.foundation.io.fluid.TankAccess;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class MachineStorageTests {
    private static MachineInventory inventory(int slots) {
        return new MachineInventory(new MachineTestContent.IO(), MachineInventoryLayout.builder().storageSlot(slots).build());
    }

    private static MachineFluidHandler fluids(MachineTestContent.IO io, int count) {
        var layout = MachineTankLayout.builder();
        for (int i = 0; i < count; i++) layout.tank(new TankAccess(), 1000);
        return new MachineFluidHandler(io, layout.build());
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void inventoryRoundTripKeepsCountsCapabilitiesAndMissingLayoutSlots(GameTestHelper helper) {
        var original = inventory(3);
        var powered = new ItemStack(SerializationTestContent.ITEM.get(), 256);
        powered.getCapability(ForgeCapabilities.ENERGY).orElseThrow(() -> new AssertionError("Missing fixture energy"))
            .receiveEnergy(731, false);
        powered.getOrCreateTag().putIntArray("typed", new int[] {1, 7, 13});
        original.setStackInSlot(0, powered);
        original.setStackInSlot(2, new ItemStack(Items.GOLD_INGOT, 17));
        var saved = original.serializeNBT();
        var smaller = inventory(1);
        smaller.deserializeNBT(saved);
        helper.assertTrue(smaller.getSlots() == 1 && smaller.getStackInSlot(0).getCount() == 256, "Saved size changed layout or truncated count");
        helper.assertTrue(smaller.getStackInSlot(0).getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new).getEnergyStored() == 731,
            "Inventory lost native Forge capability NBT");
        var expanded = inventory(3);
        expanded.deserializeNBT(smaller.serializeNBT());
        helper.assertTrue(expanded.getStackInSlot(1).isEmpty() && expanded.getStackInSlot(2).getCount() == 17,
            "Shrinking the layout discarded an unavailable slot or shifted a hole");
        expanded.getStackInSlot(0).getOrCreateTag().putInt("independent", 1);
        helper.assertTrue(!original.getStackInSlot(0).getTag().contains("independent"), "Saved inventories share item NBT");
        expanded.deserializeNBT(new CompoundTag());
        helper.assertTrue(expanded.getStackInSlot(0).isEmpty() && expanded.getStackInSlot(2).isEmpty(), "Empty reload retained old inventory contents");
        var output = OutputStack.fromNBT(OutputStack.of(powered).serializeNBT());
        helper.assertTrue(output.getItem().getCount() == 256 && output.getItem().getTag().getIntArray("typed")[2] == 13,
            "Saved determined output truncated count or typed NBT");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void inventoryRejectsMalformedLoadsWithoutPartialMutationAndReportsActualChanges(GameTestHelper helper) {
        var inventory = inventory(2);
        inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 10));
        var calls = new AtomicInteger();
        inventory.addSlotChangedCallback(slot -> calls.incrementAndGet());
        inventory.insertItem(0, new ItemStack(Items.IRON_INGOT, 5), true);
        inventory.extractItem(0, 2, true);
        helper.assertTrue(calls.get() == 0 && inventory.getStackInSlot(0).getCount() == 10, "Inventory simulation mutated contents or notified listeners");
        inventory.insertItem(0, new ItemStack(Items.IRON_INGOT, 5), false);
        inventory.extractItem(0, 2, false);
        helper.assertTrue(calls.get() == 2 && inventory.getStackInSlot(0).getCount() == 13, "Quantity changes did not notify exactly once");
        var valid = inventory.serializeNBT();
        var invalid = valid.copy();
        var list = invalid.getList("Items", Tag.TAG_COMPOUND);
        var bad = StackDataCodecs.saveItem(new ItemStack(Items.GOLD_INGOT));
        bad.putString("id", "endercore_tests:missing_item");
        bad.putInt("Slot", 1);
        list.add(bad);
        try { inventory.deserializeNBT(invalid); helper.fail("Unknown saved item accepted"); } catch (IllegalArgumentException expected) { }
        helper.assertTrue(inventory.serializeNBT().equals(valid) && calls.get() == 2, "Failed load partially changed inventory");
        invalid = valid.copy();
        invalid.getList("Items", Tag.TAG_COMPOUND).add(invalid.getList("Items", Tag.TAG_COMPOUND).getCompound(0).copy());
        try { inventory.deserializeNBT(invalid); helper.fail("Duplicate saved slot accepted"); } catch (IllegalArgumentException expected) { }
        helper.assertTrue(inventory.serializeNBT().equals(valid), "Duplicate-slot failure changed inventory");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void inventorySidedRulesAndCapacitorDataRemainNative(GameTestHelper helper) {
        var io = new MachineTestContent.IO();
        var inventory = new MachineInventory(io, MachineInventoryLayout.builder().inputSlot().outputSlot().capacitor().build());
        io.setIOMode(Direction.NORTH, IOMode.PUSH);
        io.setIOMode(Direction.SOUTH, IOMode.PULL);
        io.setIOMode(Direction.UP, IOMode.DISABLED);
        helper.assertTrue(inventory.getForSide(Direction.UP) == null, "Disabled inventory side exposed capability");
        var north = inventory.getForSide(Direction.NORTH);
        var south = inventory.getForSide(Direction.SOUTH);
        helper.assertTrue(south.insertItem(0, new ItemStack(Items.IRON_INGOT), false).isEmpty()
            && !north.insertItem(0, new ItemStack(Items.IRON_INGOT), false).isEmpty(), "Input side rules changed");
        inventory.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 2));
        helper.assertTrue(north.extractItem(0, 1, false).isEmpty() && south.extractItem(1, 1, false).isEmpty()
            && north.extractItem(1, 1, false).getCount() == 1, "Output slot or side rules changed");
        var capacitor = new ItemStack(Items.PAPER);
        helper.assertTrue(!inventory.isItemValid(2, capacitor), "Ordinary item accepted as capacitor");
        CapacitorData.ITEM_DATA.set(capacitor, CapacitorData.simple(2));
        helper.assertTrue(inventory.isItemValid(2, capacitor) && inventory.getSlotLimit(2) == 1, "Native capacitor data was not recognized");
        helper.assertTrue(inventory.isItemValid(2, new ItemStack(MachineTestContent.CAPACITOR_EXTENSION.get())),
            "Native capacitor extension capability was not recognized");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fluidSimulationNotificationsAndTagIdentityPreserveTransferAccounting(GameTestHelper helper) {
        var io = new MachineTestContent.IO();
        var handler = fluids(io, 2);
        var calls = new AtomicInteger();
        handler.addSlotChangedCallback(tank -> calls.incrementAndGet());
        var water = new FluidStack(Fluids.WATER, 1500);
        water.getOrCreateTag().putInt("variant", 3);
        helper.assertTrue(handler.fill(water, FluidAction.SIMULATE) == 1500 && handler.getFluidInTank(0).isEmpty() && calls.get() == 0,
            "Fluid fill simulation mutated contents or notified listeners");
        helper.assertTrue(handler.fill(water, FluidAction.EXECUTE) == 1500 && calls.get() == 2 && water.getAmount() == 1500,
            "Real multi-tank fill changed source amount or notified more than once per tank");
        var wrong = new FluidStack(Fluids.WATER, 500);
        wrong.getOrCreateTag().putInt("variant", 4);
        helper.assertTrue(handler.fill(wrong, FluidAction.EXECUTE) == 0 && handler.drain(wrong, FluidAction.EXECUTE).isEmpty(), "Fluid NBT variants mixed");
        var drained = handler.drain(400, FluidAction.SIMULATE);
        helper.assertTrue(drained.getAmount() == 400 && calls.get() == 2 && handler.getFluidInTank(0).getAmount() == 1000,
            "Fluid drain simulation changed state");
        helper.assertTrue(handler.drain(400, FluidAction.EXECUTE).getAmount() == 400 && calls.get() == 3, "Executed drain notified more than once");
        helper.assertTrue(handler.drain(-1, FluidAction.EXECUTE).isEmpty() && calls.get() == 3, "Negative drain changed fluid amount");
        io.setIOMode(Direction.NORTH, IOMode.PULL);
        io.setIOMode(Direction.SOUTH, IOMode.DISABLED);
        helper.assertTrue(handler.getForSide(Direction.NORTH).drain(10, FluidAction.EXECUTE).isEmpty()
            && handler.getForSide(Direction.SOUTH) == null, "Fluid side configuration was ignored");
        for (int tank : new int[] {-1, 2}) {
            try { handler.getTank(tank); helper.fail("Invalid tank index accepted"); } catch (IndexOutOfBoundsException expected) { }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fluidNbtReloadClearsOldContentsAndRetainsUnavailableTanks(GameTestHelper helper) {
        var original = fluids(new MachineTestContent.IO(), 2);
        var lava = new FluidStack(Fluids.LAVA, 750);
        lava.getOrCreateTag().putLong("typed", 9876543210L);
        original.setFluidInTank(1, lava);
        lava.setAmount(1);
        helper.assertTrue(original.getFluidInTank(1).getAmount() == 750, "Setting a fluid tank retained caller-owned stack");
        var smaller = fluids(new MachineTestContent.IO(), 1);
        smaller.deserializeNBT(original.serializeNBT());
        var expanded = fluids(new MachineTestContent.IO(), 2);
        expanded.deserializeNBT(smaller.serializeNBT());
        helper.assertTrue(expanded.getFluidInTank(0).isEmpty() && expanded.getFluidInTank(1).getAmount() == 750
            && expanded.getFluidInTank(1).getTag().getLong("typed") == 9876543210L, "Fluid save lost index, amount or NBT");
        var before = expanded.serializeNBT();
        var duplicate = before.copy();
        var entries = duplicate.getList(CoreNBTKeys.TANKS, Tag.TAG_COMPOUND);
        entries.add(entries.getCompound(0).copy());
        try { expanded.deserializeNBT(duplicate); helper.fail("Duplicate tank index accepted"); } catch (IllegalArgumentException expected) { }
        helper.assertTrue(expanded.serializeNBT().equals(before), "Failed fluid load partially changed tanks");
        expanded.deserializeNBT(new CompoundTag());
        helper.assertTrue(expanded.getFluidInTank(1).isEmpty(), "Empty reload retained old fluid");
        var output = OutputStack.fromNBT(OutputStack.of(original.getFluidInTank(1)).serializeNBT());
        helper.assertTrue(output.getFluid().getAmount() == 750 && output.getFluid().getTag().getLong("typed") == 9876543210L,
            "Saved fluid recipe output lost quantity or NBT");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void energyTransfersRejectNegativeAmountsOverflowAndLoadOrderLoss(GameTestHelper helper) {
        var capacity = new AtomicInteger(Integer.MAX_VALUE);
        var notifications = new AtomicInteger();
        var io = new MachineTestContent.IO();
        var storage = new MachineEnergyStorage(io, EnergyIOMode.Both, capacity::get, () -> 250) {
            @Override protected void onContentsChanged() { notifications.incrementAndGet(); }
        };
        storage.setEnergyStored(Integer.MAX_VALUE - 5);
        int initialCalls = notifications.get();
        helper.assertTrue(storage.receiveEnergy(50, true) == 5 && notifications.get() == initialCalls,
            "Energy simulation overflowed or notified a mutation");
        helper.assertTrue(storage.receiveEnergy(50, false) == 5 && storage.getEnergyStored() == Integer.MAX_VALUE
            && notifications.get() == initialCalls + 1, "Energy receive overflowed or notified twice");
        helper.assertTrue(storage.extractEnergy(-1, false) == 0 && storage.addEnergy(-1) == 0 && storage.takeEnergy(-1) == 0
            && storage.consumeEnergy(-1, false) == 0 && storage.getEnergyStored() == Integer.MAX_VALUE, "Negative energy operation changed storage");
        helper.assertTrue(storage.consumeEnergy(1000, false) == 250 && notifications.get() == initialCalls + 2, "Energy use ignored its rate or notified twice");
        capacity.set(0);
        var saved = storage.serializeNBT();
        var restored = new MachineEnergyStorage(io, EnergyIOMode.Both, capacity::get, () -> 250);
        restored.deserializeNBT(saved);
        helper.assertTrue(restored.getEnergyStored() == 0 && restored.receiveEnergy(1, false) == 0 && restored.serializeNBT().equals(saved),
            "Uninitialized capacity exposed charge or erased it while saving");
        capacity.set(Integer.MAX_VALUE);
        helper.assertTrue(restored.getEnergyStored() == Integer.MAX_VALUE - 250, "Capacity initialization erased stored energy");
        restored.setEnergyStored(500);
        capacity.set(100);
        helper.assertTrue(restored.getEnergyStored() == 100 && restored.extractEnergy(50, false) == 50, "Reduced capacity exposure or extraction changed");
        capacity.set(1000);
        helper.assertTrue(restored.getEnergyStored() == 450, "Extracting during reduced capacity discarded hidden stored charge");
        io.setIOMode(Direction.NORTH, IOMode.PULL);
        io.setIOMode(Direction.SOUTH, IOMode.DISABLED);
        helper.assertTrue(restored.getForSide(Direction.NORTH).extractEnergy(10, false) == 0 && restored.getForSide(Direction.SOUTH) == null,
            "Energy side rules changed");
        var inputOnly = new MachineEnergyStorage(io, EnergyIOMode.Input, () -> 1000, () -> 100);
        var retainedSide = inputOnly.getForSide(Direction.NORTH);
        io.setIOMode(Direction.NORTH, IOMode.DISABLED);
        helper.assertTrue(retainedSide.receiveEnergy(50, false) == 0, "Retained energy view bypassed a disabled side");
        io.setIOMode(Direction.NORTH, IOMode.PUSH);
        helper.assertTrue(retainedSide.receiveEnergy(50, false) == 50, "Input-only machine incorrectly applied directional IO restrictions");
        helper.succeed();
    }
}
