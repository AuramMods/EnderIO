package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.content.machines.soul_engine.SoulEngineBlockEntity;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

public class SoulEngineTests {
    @GameTest
    public static void blazeBurnsOneMillibucketAtItsAdvertisedRate(GameplayTest test) {
        engineTemplate(test);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(0, 1, 0, SoulEngineBlockEntity.class);
            var tank = machine.getFluidHandler();
            machine.bindSoul(Soul.of(EntityType.BLAZE));
            helper.insertIntoContainer(0, 1, 0, EIOItems.BASIC_CAPACITOR.get(), 1);
            helper.assertValueEqual(tank.fill(new FluidStack(Fluids.WATER, 2), FluidAction.EXECUTE), 0, "reject water");
            helper.assertValueEqual(tank.fill(new FluidStack(Fluids.LAVA, 2), FluidAction.EXECUTE), 2, "accept blaze fuel");

            tick(machine, 17);
            helper.assertValueEqual(machine.getEnergyStorage().getEnergyStored(), 0, "blaze energy before paying 18 ticks");
            helper.assertValueEqual(machine.getFluidTank().getFluidAmount(), 2, "unspent lava before paying 18 ticks");
            machine.serverTick();
            helper.assertValueEqual(machine.getEnergyStorage().getEnergyStored(), 800, "exact blaze output after 18 ticks");
            helper.assertValueEqual(machine.getFluidTank().getFluidAmount(), 1, "one millibucket consumed");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void removingOrChangingSoulConservesTheOldFuel(GameplayTest test) {
        engineTemplate(test);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(0, 1, 0, SoulEngineBlockEntity.class);
            helper.insertIntoContainer(0, 1, 0, EIOItems.BASIC_CAPACITOR.get(), 1);
            machine.bindSoul(Soul.of(EntityType.BLAZE));
            machine.getFluidHandler().fill(new FluidStack(Fluids.LAVA, 2), FluidAction.EXECUTE);
            machine.bindSoul(Soul.of(EntityType.ZOMBIE));
            tick(machine, 20);
            helper.assertValueEqual(machine.getEnergyStorage().getEnergyStored(), 0, "zombie must not convert lava to power");
            helper.assertValueEqual(machine.getFluidTank().getFluidAmount(), 2, "keep lava available for extraction");
            helper.assertTrue(!machine.isActive(), "engine is inactive with mismatched fuel");
            machine.bindSoul(Soul.EMPTY);
            machine.serverTick();
            helper.assertTrue(machine.getMachineStates().contains(MachineState.NOT_SOULBOUND), "unbound status follows the actual soul");
            machine.bindSoul(Soul.of(EntityType.BLAZE));
            machine.serverTick();
            helper.assertTrue(!machine.getMachineStates().contains(MachineState.NOT_SOULBOUND), "valid binding clears the unbound status");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void engineWaitsForCapacitorRedstoneAndSpaceForItsFullOutput(GameplayTest test) {
        engineTemplate(test);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(0, 1, 0, SoulEngineBlockEntity.class);
            var storage = machine.getEnergyStorage();
            machine.bindSoul(Soul.of(EntityType.BLAZE));
            machine.getFluidHandler().fill(new FluidStack(Fluids.LAVA, 2), FluidAction.EXECUTE);
            tick(machine, 20);
            helper.assertValueEqual(machine.saveWithoutMetadata().getInt("BurnedTicks"), 0, "no burn progress without a capacitor");
            helper.insertIntoContainer(0, 1, 0, EIOItems.BASIC_CAPACITOR.get(), 1);
            machine.setRedstoneControl(RedstoneControl.NEVER_ACTIVE);
            tick(machine, 20);
            helper.assertValueEqual(machine.saveWithoutMetadata().getInt("BurnedTicks"), 0, "no progress while disabled");
            int capacity = storage.getMaxEnergyStored();
            storage.setEnergyStored(capacity - 799);
            machine.setRedstoneControl(RedstoneControl.ALWAYS_ACTIVE);
            tick(machine, 20);
            helper.assertValueEqual(storage.getEnergyStored(), capacity - 799, "full quantum does not fit");
            helper.assertValueEqual(machine.getFluidTank().getFluidAmount(), 2, "no fuel lost to a nearly full buffer");
            helper.assertTrue(machine.getMachineStates().contains(MachineState.FULL_POWER), "blocked output is visible in machine status");
            storage.extractEnergy(1, false);
            tick(machine, 18);
            helper.assertValueEqual(storage.getEnergyStored(), capacity, "exact capacity reached with a full output");
            helper.assertValueEqual(machine.getFluidTank().getFluidAmount(), 1, "one millibucket supplies the final full quantum");
            helper.startSequence().thenSucceed();
        });
    }

    private static void engineTemplate(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.SOUL_ENGINE.get().defaultBlockState()));
    }

    private static void tick(SoulEngineBlockEntity engine, int count) {
        for (int i = 0; i < count; i++) engine.serverTick();
    }
}
