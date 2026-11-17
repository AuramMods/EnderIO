package com.enderio.core.gametest;

import com.enderio.core.common.recipes.SizedIngredient;
import com.enderio.enderio.api.capacitor.CapacitorData;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
import com.enderio.enderio.foundation.energy.PoweredMachineEnergyStorage;
import com.enderio.enderio.foundation.state.MachineState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.enderio.core.gametest.PoweredMachineTestContent.*;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class PoweredMachineTests {
    private static Machine create(GameTestHelper helper, Block block, BlockPos pos) {
        helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        return (Machine) helper.getLevel().getBlockEntity(pos);
    }
    private static void install(Machine machine, ItemStack capacitor) { machine.getInventory().setStackInSlot(machine.getCapacitorSlotIndex(), capacitor); }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void poweredStorageBoundsSimulationAndInternalConsumptionConserveEnergy(GameTestHelper helper) {
        var machine = create(helper, INPUT.get(), helper.absolutePos(new BlockPos(1, 2, 1)));
        install(machine, capacitor(1));
        var energy = machine.getEnergyStorage();
        energy.setEnergyStored(500);
        int dirty = machine.dirtyCalls;
        helper.assertTrue(energy.addEnergy(-3) == 0 && energy.consumeEnergy(-3) == 0 && energy.getEnergyStored() == 500,
            "Negative internal energy operation changed stored charge");
        helper.assertTrue(energy.addEnergy(100, true) == 100 && energy.consumeEnergy(100, true) == 10
            && energy.receiveEnergy(100, true) == 100 && energy.getEnergyStored() == 500 && machine.dirtyCalls == dirty,
            "Simulation changed native machine state or ignored the consumption rate");
        energy.setEnergyStored(500);
        helper.assertTrue(machine.dirtyCalls == dirty, "Unchanged energy dirtied the machine");
        helper.assertTrue(energy.consumeEnergy(100) == 10 && energy.takeEnergy(20) == 20 && energy.getEnergyStored() == 470,
            "Internal energy consumption incorrectly used external output permissions");
        install(machine, capacitor(Float.MAX_VALUE));
        energy.setEnergyStored(Integer.MAX_VALUE - 7);
        helper.assertTrue(energy.addEnergy(100) == 7 && energy.getEnergyStored() == Integer.MAX_VALUE,
            "Positive addition overflowed the integer energy store");
        energy.setEnergyStored(-1);
        helper.assertTrue(energy.getEnergyStored() == 0, "Negative direct energy state remained visible");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void capacitorDowngradeAndNativeWorldItemLifecycleRetainStoredCharge(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var machine = create(helper, INPUT.get(), pos);
        install(machine, capacitor(2));
        machine.getEnergyStorage().setEnergyStored(3200);
        install(machine, capacitor(1));
        helper.assertTrue(machine.getMaxEnergyStored() == 1000 && machine.getEnergyStorage().addEnergy(1) == 0
            && machine.getEnergyStorage().getEnergyStored() == 3200, "Capacity decrease erased saved charge during an add");
        var saved = machine.saveWithFullMetadata();
        var restored = (Machine) BlockEntity.loadStatic(pos, machine.getBlockState(), saved);
        helper.assertTrue(restored != null && restored.getEnergyStorage().getEnergyStored() == 3200,
            "Native load clamped energy before capacitor/level restoration");
        restored.setLevel(helper.getLevel());
        restored.onLoad();
        helper.assertTrue(restored.getMaxEnergyStored() == 1000 && restored.getEnergyStorage().getEnergyStored() == 3200,
            "Level attachment lost preserved excess charge");
        var item = new ItemStack(MACHINE_ITEM.get());
        restored.saveToItem(item);
        helper.assertTrue(PoweredMachineEnergyStorage.ITEM_DATA.getOrDefault(item, 0) == 3200, "Powered item export lost raw charge");
        PoweredMachineEnergyStorage.ITEM_DATA.set(item, 777);
        helper.getLevel().removeBlock(pos, false);
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false);
        var context = new BlockPlaceContext(new UseOnContext(helper.getLevel(), null, InteractionHand.MAIN_HAND, item, hit));
        helper.assertTrue(((BlockItem) item.getItem()).place(context).consumesAction(), "Powered item placement failed");
        var placed = (Machine) helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(placed.getEnergyStorage().getEnergyStored() == 777 && placed.getMaxEnergyStored() == 1000,
            "Stale block NBT overrode modified carried energy or capacitor data");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void retainedPoweredCapabilitiesRespectDisabledSidesAndRemovedOwners(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        for (var block : new Block[] {INPUT.get(), OUTPUT.get(), BOTH.get()}) {
            var machine = create(helper, block, pos);
            install(machine, capacitor(1));
            machine.getEnergyStorage().setEnergyStored(500);
            var capability = machine.getCapability(ForgeCapabilities.ENERGY, Direction.UP);
            var retained = capability.orElseThrow(AssertionError::new);
            machine.setIOMode(Direction.UP, IOMode.DISABLED);
            helper.assertTrue(!capability.isPresent() && !retained.canReceive() && !retained.canExtract()
                && retained.receiveEnergy(50, false) == 0 && retained.extractEnergy(50, false) == 0
                && machine.getEnergyStorage().getEnergyStored() == 500, "Retained powered capability bypassed a disabled side");
            machine.setIOMode(Direction.UP, IOMode.NONE);
            var active = machine.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(AssertionError::new);
            helper.getLevel().removeBlock(pos, false);
            helper.assertTrue(!active.canReceive() && !active.canExtract() && active.receiveEnergy(50, false) == 0
                && active.extractEnergy(50, false) == 0, "Removed machine retained an active energy endpoint");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nullableCapacitorExtensionsAndCapacitorFreeMachinesAreSafe(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var machine = create(helper, INPUT.get(), pos);
        install(machine, new ItemStack(NULL_EXTENSION.get()));
        helper.assertTrue(!machine.isCapacitorInstalled() && machine.getCapacitorData().equals(CapacitorData.NONE)
            && machine.getMaxEnergyStored() == 0 && machine.getMachineStates().contains(MachineState.NO_CAPACITOR),
            "Nullable extension data did not represent an absent capacitor");
        var fixed = create(helper, NO_CAPACITOR.get(), pos.east());
        helper.assertTrue(fixed.getCapacitorItem().isEmpty() && !fixed.isCapacitorInstalled()
            && fixed.getMaxEnergyStored() == 1000 && fixed.getMaxEnergyUse() == 10, "Capacitor-free machine queried an invalid inventory slot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeCapacitorInstallPreservesCreativeStacksAndOccupiedSlots(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var machine = create(helper, INPUT.get(), pos);
        try (var peer = new MachineInteractionTests.Peer(helper, pos)) {
            var held = capacitor(2); held.setCount(3);
            peer.player.setItemInHand(InteractionHand.MAIN_HAND, held);
            helper.assertTrue(peer.use(pos, InteractionHand.MAIN_HAND).consumesAction() && held.getCount() == 2
                && machine.getCapacitorItem().getCount() == 1 && machine.getMaxEnergyStored() == 4000, "Native survival installation changed stack accounting");
            peer.player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
            install(machine, ItemStack.EMPTY);
            peer.use(pos, InteractionHand.MAIN_HAND);
            helper.assertTrue(held.getCount() == 2 && machine.getCapacitorItem().getCount() == 1, "onItemUseFirst consumed a creative capacitor");
            install(machine, capacitor(0));
            peer.use(pos, InteractionHand.MAIN_HAND);
            helper.assertTrue(CapacitorData.ITEM_DATA.get(machine.getCapacitorItem()).base() == 0 && held.getCount() == 2,
                "Installation silently replaced an occupied zero-strength capacitor");
            install(machine, ItemStack.EMPTY);
            peer.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CAPACITOR.get()));
            peer.use(pos, InteractionHand.MAIN_HAND);
            helper.assertTrue(machine.getCapacitorItem().isEmpty(), "Capacitor installation bypassed native slot validity");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void dynamicCapacitorUseRefreshesScalingWithoutDiscardingCharge(GameTestHelper helper) {
        var machine = create(helper, INPUT.get(), helper.absolutePos(new BlockPos(1, 2, 1)));
        var capacitor = new ItemStack(DYNAMIC_EXTENSION.get()); capacitor.getOrCreateTag().putInt("strength", 2);
        install(machine, capacitor);
        machine.getEnergyStorage().setEnergyStored(3500);
        helper.assertTrue(machine.getMaxEnergyStored() == 4000, "Dynamic extension was not queried");
        int dirty = machine.dirtyCalls;
        machine.completeOperation();
        helper.assertTrue(machine.getMaxEnergyStored() == 1000 && machine.getMaxEnergyUse() == 10
            && machine.getEnergyStorage().getEnergyStored() == 3500 && machine.dirtyCalls > dirty,
            "Capacitor use did not refresh scaling/persistence or erased excess energy");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void poweredTaskAndMachineSaveResumeOnceWithRedstonePause(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var machine = create(helper, INPUT.get(), pos);
        var recipe = new AlloySmeltingRecipe(List.of(SizedIngredient.of(Items.STONE, 1)), new ItemStack(Items.IRON_INGOT, 2), 100, 0)
            .withId(ResourceLocation.fromNamespaceAndPath("endercore_tests", "powered_task"));
        var original = new ArrayList<>(helper.getLevel().getRecipeManager().getRecipes());
        var recipes = new ArrayList<>(original); recipes.add(recipe);
        helper.getLevel().getRecipeManager().replaceRecipes(recipes);
        try {
            install(machine, capacitor(1));
            INPUT_SLOT.setStackInSlot(machine, new ItemStack(Items.STONE));
            machine.getEnergyStorage().setEnergyStored(100);
            for (int i = 0; i < 5; i++) machine.serverTick();
            helper.assertTrue(machine.tasks.getProgress() == 0.5f && machine.getEnergyStorage().getEnergyStored() == 50,
                "Powered task did not consume the actual machine's capped energy");
            var saved = machine.saveWithFullMetadata();
            var restored = (Machine) BlockEntity.loadStatic(pos, machine.getBlockState(), saved);
            helper.getLevel().removeBlockEntity(pos);
            helper.getLevel().setBlockEntity(restored);
            restored.onLoad();
            restored.setRedstoneControl(RedstoneControl.NEVER_ACTIVE);
            for (int i = 0; i < 3; i++) restored.serverTick();
            helper.assertTrue(restored.tasks.getProgress() == 0.5f && restored.getEnergyStorage().getEnergyStored() == 50,
                "Redstone pause advanced the restored task");
            restored.setRedstoneControl(RedstoneControl.ALWAYS_ACTIVE);
            for (int i = 0; i < 8; i++) restored.serverTick();
            helper.assertTrue(!restored.tasks.hasTask() && restored.getEnergyStorage().getEnergyStored() == 0
                && INPUT_SLOT.getItemStack(restored).isEmpty() && OUTPUT_SLOT.getItemStack(restored).getCount() == 2,
                "Native powered machine resume duplicated or lost energy, input or output");
        } finally { helper.getLevel().getRecipeManager().replaceRecipes(original); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void poweredGeneratorPushUsesNativeCapabilitiesAndHonorsDisabledSides(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var source = create(helper, OUTPUT.get(), pos);
        var target = create(helper, INPUT.get(), pos.east());
        install(source, capacitor(1)); install(target, capacitor(1));
        source.getEnergyStorage().setEnergyStored(50);
        source.setIOMode(Direction.EAST, IOMode.DISABLED);
        source.serverTick();
        helper.assertTrue(source.getEnergyStorage().getEnergyStored() == 50 && target.getEnergyStorage().getEnergyStored() == 0,
            "Generator pushed energy through a disabled side");
        source.setIOMode(Direction.EAST, IOMode.NONE);
        source.serverTick();
        helper.assertTrue(source.getEnergyStorage().getEnergyStored() == 0 && target.getEnergyStorage().getEnergyStored() == 50,
            "Native generator transfer lost or duplicated charge");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void historicalEnergyAndIoFieldsLoadWithoutOverridingCurrentData(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var machine = create(helper, INPUT.get(), pos);
        install(machine, capacitor(1));
        var tag = machine.saveWithFullMetadata();
        tag.remove("EnergyStored");
        tag.remove("IOConfig");
        // Literal keys and scalar formats read from the recorded historical Forge source.
        var energy = new CompoundTag(); energy.putInt("EnergyStored", 721); tag.put("Energy", energy);
        var io = new CompoundTag(); io.putByte("1", (byte) 4); tag.put("IoConfig", io);
        tag.putInt("RedstoneControl", 3);
        var copy = tag.copy();
        var restored = (Machine) BlockEntity.loadStatic(pos, machine.getBlockState(), tag);
        helper.assertTrue(restored != null && restored.getEnergyStorage().getEnergyStored() == 721
            && restored.getIOMode(Direction.UP) == IOMode.DISABLED && restored.getRedstoneControl() == RedstoneControl.NEVER_ACTIVE,
            "Historical Energy/IoConfig/RedstoneControl fields were not restored by the actual powered base");
        helper.assertTrue(tag.equals(copy), "Migration modified caller-owned legacy NBT");
        tag.putInt("EnergyStored", 444);
        tag.put("IOConfig", com.enderio.enderio.foundation.io.IOConfig.of(IOMode.PUSH).save());
        restored.load(tag);
        helper.assertTrue(restored.getEnergyStorage().getEnergyStored() == 444 && restored.getIOMode(Direction.UP) == IOMode.PUSH,
            "Legacy values overrode current native state");
        restored.setLevel(helper.getLevel());
        tag.putInt("EnergyStored", -10);
        restored.load(tag);
        helper.assertTrue(restored.getEnergyStorage().getEnergyStored() == 0 && restored.getMachineStates().contains(MachineState.NO_POWER),
            "Invalid native charge remained negative or left a stale power state");
        tag.remove("EnergyStored"); tag.getCompound("Energy").putInt("EnergyStored", -10);
        restored.load(tag);
        helper.assertTrue(restored.getEnergyStorage().getEnergyStored() == 0, "Invalid historical charge remained negative");
        helper.succeed();
    }
}
