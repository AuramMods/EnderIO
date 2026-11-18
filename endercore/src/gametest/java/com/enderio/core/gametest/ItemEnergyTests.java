package com.enderio.core.gametest;

import com.enderio.core.common.energy.NbtEnergyStorage;
import com.enderio.core.common.energy.StackedNbtEnergyStorage;
import com.enderio.core.common.item.ItemDataKey;
import com.mojang.serialization.Codec;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.enderio.core.gametest.ItemDataTestContent.*;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class ItemEnergyTests {
    private static final ItemDataKey<Integer> CHARGE = new ItemDataKey<>(id("live_energy"), Codec.intRange(0, Integer.MAX_VALUE), false);

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void transfersRespectCapacityLimitsAndSimulation(GameTestHelper helper) {
        var stack = BUILDER_ITEM.toStack();
        var energy = new NbtEnergyStorage(stack, CHARGE, 100, 60, 25);
        helper.assertTrue(energy.receiveEnergy(200, true) == 60 && !stack.hasTag(), "Simulation mutated the item or ignored receive limit");
        helper.assertTrue(energy.receiveEnergy(200, false) == 60, "Wrong receive amount");
        helper.assertTrue(energy.receiveEnergy(200, false) == 40 && energy.getEnergyStored() == 100, "Capacity was exceeded");
        var before = stack.save(new CompoundTag());
        helper.assertTrue(energy.extractEnergy(200, true) == 25 && before.equals(stack.save(new CompoundTag())), "Extract simulation mutated the item");
        helper.assertTrue(energy.extractEnergy(200, false) == 25 && energy.getEnergyStored() == 75, "Extraction accounting failed");
        helper.assertTrue(energy.receiveEnergy(-1, false) == 0 && energy.extractEnergy(Integer.MIN_VALUE, false) == 0,
            "Negative transfer created/destroyed energy");
        helper.assertTrue(energy.getEnergyStored() == 75, "Rejected transfer changed storage");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void liveViewsCopiesAndSaveLoadStayConsistent(GameTestHelper helper) {
        var stack = BUILDER_ITEM.toStack();
        var first = new NbtEnergyStorage(stack, CHARGE, 1000);
        var second = new NbtEnergyStorage(stack, CHARGE, 1000);
        first.receiveEnergy(400, false);
        helper.assertTrue(second.getEnergyStored() == 400, "Another live view retained stale energy");
        var copy = stack.copy();
        var copied = new NbtEnergyStorage(copy, CHARGE, 1000);
        copied.extractEnergy(123, false);
        helper.assertTrue(first.getEnergyStored() == 400 && copied.getEnergyStored() == 277, "Copy shared energy with original");
        var loaded = ItemStack.of(copy.save(new CompoundTag()));
        helper.assertTrue(new NbtEnergyStorage(loaded, CHARGE, 1000).getEnergyStored() == 277, "Native save/load lost charge");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void stackedTransfersPreservePerItemChargeAndRemainders(GameTestHelper helper) {
        var stack = BUILDER_ITEM.toStack(3);
        var energy = new StackedNbtEnergyStorage(stack, CHARGE, 100);
        helper.assertTrue(energy.receiveEnergy(10, true) == 9 && !stack.hasTag(), "Stack simulation changed NBT or rounded up");
        helper.assertTrue(energy.receiveEnergy(10, false) == 9 && energy.getEnergyStored() == 3, "Stack receive lost its remainder");
        helper.assertTrue(energy.extractEnergy(2, false) == 0, "Partial per-item extraction was accepted");
        helper.assertTrue(energy.extractEnergy(7, false) == 6 && energy.getEnergyStored() == 1, "Stack extraction accounting failed");
        helper.assertTrue(energy.receiveEnergy(Integer.MAX_VALUE, false) == 297, "Stack capacity overflowed");
        stack.shrink(3);
        helper.assertTrue(energy.extractEnergy(100, false) == 0 && energy.receiveEnergy(100, false) == 0,
            "A retained provider transferred from an emptied stack");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void integerBoundaryAndDisabledTransfersAreSafe(GameTestHelper helper) {
        var stack = BUILDER_ITEM.toStack();
        var energy = new NbtEnergyStorage(stack, CHARGE, Integer.MAX_VALUE);
        helper.assertTrue(energy.receiveEnergy(Integer.MAX_VALUE, false) == Integer.MAX_VALUE, "Maximum capacity was truncated");
        helper.assertTrue(energy.receiveEnergy(1, false) == 0, "Full capacity overflowed");
        helper.assertTrue(energy.extractEnergy(Integer.MAX_VALUE, false) == Integer.MAX_VALUE && energy.getEnergyStored() == 0,
            "Maximum extraction overflowed");
        var disabled = new NbtEnergyStorage(stack, CHARGE, 100, 0, 0);
        helper.assertTrue(!disabled.canReceive() && !disabled.canExtract() && disabled.receiveEnergy(1, false) == 0,
            "Disabled transfers accepted energy");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void capacityReductionDoesNotEraseStoredCharge(GameTestHelper helper) {
        var stack = BUILDER_ITEM.toStack();
        CHARGE.set(stack, 200);
        var energy = new NbtEnergyStorage(stack, CHARGE, 100);
        helper.assertTrue(energy.receiveEnergy(1, false) == 0 && energy.getEnergyStored() == 200, "Lower capacity erased existing charge");
        helper.assertTrue(energy.extractEnergy(100, false) == 100 && energy.getEnergyStored() == 100, "Existing excess charge could not be recovered");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void forge6ChargeMigratesWithoutRefillingOrMutatingSimulations(GameTestHelper helper) {
        var stack = BUILDER_ITEM.toStack();
        var old = stack.getOrCreateTagElement("Energy");
        old.putInt("EnergyStored", 150);
        old.putInt("MaxEnergyStored", 200);
        old.putInt("MaxEnergyUse", 200);
        old.putString("Unrelated", "preserved");
        var before = stack.save(new CompoundTag());
        var energy = NbtEnergyStorage.withForge6Charge(stack, CHARGE, 100);
        helper.assertTrue(energy.getEnergyStored() == 150 && energy.receiveEnergy(100, true) == 0
            && energy.extractEnergy(200, true) == 100 && before.equals(stack.save(new CompoundTag())),
            "Legacy balance was clamped or simulation rewrote old NBT");
        helper.assertTrue(new NbtEnergyStorage(stack, CHARGE, 100).getEnergyStored() == 0,
            "An item without legacy support used the old balance");

        var current = stack.copy();
        CHARGE.set(current, 0);
        helper.assertTrue(NbtEnergyStorage.withForge6Charge(current, CHARGE, 100).getEnergyStored() == 0,
            "The legacy balance replaced an explicitly empty current balance");

        helper.assertTrue(energy.extractEnergy(200, false) == 100 && CHARGE.getOrDefault(stack, -1) == 50
            && energy.extractEnergy(200, false) == 50 && CHARGE.getOrDefault(stack, -1) == 0,
            "Execution failed to account for historical charge exactly once");
        var restored = ItemStack.of(stack.save(new CompoundTag()));
        var remainder = restored.getTagElement("Energy");
        helper.assertTrue(remainder != null && remainder.getString("Unrelated").equals("preserved")
            && !remainder.contains("EnergyStored", Tag.TAG_INT) && NbtEnergyStorage.withForge6Charge(restored, CHARGE, 100).getEnergyStored() == 0,
            "Draining or reloading resurrected old charge or lost unrelated NBT");
        helper.succeed();
    }
}
