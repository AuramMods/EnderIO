package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.content.machines.drain.DrainBlockEntity;
import com.enderio.enderio.foundation.attachment.ActionRange;
import com.enderio.enderio.foundation.state.MachineState;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

public class DrainTests {
    private static final BlockPos MACHINE = new BlockPos(1, 4, 1);
    private static final BlockPos INTAKE = MACHINE.below();
    private static final int CHARGE = 4_000;

    @GameTest
    public static void zeroRangeCollectsTheIntakeForExactlyOnePayment(GameplayTest test) {
        registerDrain(test);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var drain = chargedDrain(helper);
            drain.setActionRange(new ActionRange(0, false));
            helper.setBlock(INTAKE, Blocks.LAVA);
            tick(drain, 200);
            helper.assertBlockPresent(Blocks.AIR, INTAKE);
            helper.assertTrue(drain.getFluidTank().getFluid().isFluidStackIdentical(new FluidStack(Fluids.LAVA, 1_000)),
                "the range-zero intake produces one bucket of lava");
            helper.assertValueEqual(drain.getEnergyStorage().getEnergyStored(), CHARGE - 1_500,
                "the intake is paid for exactly once");
            helper.assertTrue(drain.getMachineStates().contains(MachineState.NO_SOURCE),
                "the empty intake reports that there is no source");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void drainChecksTheLastPositionBeforeConsumingTheIntake(GameplayTest test) {
        registerDrain(test);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var drain = chargedDrain(helper);
            drain.setActionRange(new ActionRange(1, false));
            drain.getEnergyStorage().setEnergyStored(1_500);
            var last = INTAKE.offset(1, 0, 1);
            helper.setBlock(INTAKE, Blocks.WATER);
            helper.setBlock(last, Blocks.WATER);
            tick(drain, 240);
            helper.assertBlockPresent(Blocks.AIR, last);
            helper.assertBlockPresent(Blocks.WATER, INTAKE);
            helper.assertValueEqual(drain.getFluidTank().getFluidAmount(), 1_000,
                "the farthest source was the only bucket collected");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void drainCollectsWaterWithoutDeletingTheWaterloggedBlock(GameplayTest test) {
        registerDrain(test);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var drain = chargedDrain(helper);
            drain.setActionRange(new ActionRange(1, false));
            drain.getEnergyStorage().setEnergyStored(1_500);
            var fence = INTAKE.offset(-1, -2, -1);
            helper.setBlock(INTAKE, Blocks.WATER);
            helper.setBlock(fence, Blocks.OAK_FENCE.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
            tick(drain, 200);
            helper.assertBlockPresent(Blocks.OAK_FENCE, fence);
            helper.assertTrue(helper.getBlockState(fence).getFluidState().isEmpty(), "the fence is dry after pickup");
            helper.assertBlockPresent(Blocks.WATER, INTAKE);
            helper.assertValueEqual(drain.getFluidTank().getFluid().getFluid(), Fluids.WATER, "the tank contains water");
            helper.assertValueEqual(drain.getFluidTank().getFluidAmount(), 1_000, "the tank contains one bucket");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void oldPlacedDrainRetainsItsRangeAndPartialPayment(GameplayTest test) {
        registerDrain(test);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var drain = chargedDrain(helper);
            var saved = drain.saveWithoutMetadata();
            saved.putInt("Range", 0);
            saved.putBoolean("RangeVisible", true);
            saved.putInt(DrainBlockEntity.CONSUMED, 1_327);
            drain.load(saved);
            helper.assertValueEqual(drain.getActionRange(), new ActionRange(0, true), "Forge 6 range and visibility");
            helper.setBlock(INTAKE, Blocks.WATER);
            tick(drain, 25);
            helper.assertBlockPresent(Blocks.AIR, INTAKE);
            helper.assertValueEqual(drain.getEnergyStorage().getEnergyStored(), CHARGE - 173,
                "only the unpaid part of the saved job is charged");
            helper.assertValueEqual(drain.saveWithoutMetadata().getInt(DrainBlockEntity.CONSUMED), 0,
                "the saved payment is cleared when the bucket arrives");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void oldDrainItemRestoresItsCarriedData(GameplayTest test) {
        registerDrain(test);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var legacy = new CompoundTag();
            legacy.putString("id", "enderio:drain");
            legacy.putInt("Range", 2);
            legacy.putBoolean("RangeVisible", true);
            legacy.putInt("RedstoneControl", 3);
            legacy.putInt("Consumed", 741);
            var energy = new CompoundTag();
            energy.putInt("EnergyStored", 9_000);
            legacy.put("Energy", energy);
            var fluid = new CompoundTag();
            fluid.putInt("Index", 0);
            fluid.putString("FluidName", "minecraft:lava");
            fluid.putInt("Amount", 1_231);
            var tanks = new ListTag();
            tanks.add(fluid);
            var storage = new CompoundTag();
            storage.put("Tanks", tanks);
            legacy.put("Fluid", storage);

            var original = new ItemStack(EIOBlocks.DRAIN.get());
            original.addTagElement(BlockItem.BLOCK_ENTITY_TAG, legacy);
            var migrated = ItemStack.of(original.save(new CompoundTag()));
            helper.assertValueEqual(EIODataComponents.ACTION_RANGE.get(migrated), new ActionRange(2, true), "carried range");
            helper.assertValueEqual(EIODataComponents.ENERGY.get(migrated), 9_000, "carried energy");
            helper.assertValueEqual(RedstoneControl.ITEM_DATA.get(migrated), RedstoneControl.NEVER_ACTIVE, "carried redstone");
            helper.assertTrue(EIODataComponents.ITEM_FLUID_CONTENT.getOrDefault(migrated, FluidStack.EMPTY)
                .isFluidStackIdentical(new FluidStack(Fluids.LAVA, 1_231)), "carried lava");
            var remaining = BlockItem.getBlockEntityData(migrated);
            helper.assertTrue(remaining != null && !remaining.contains("Fluid") && remaining.getInt("Consumed") == 741,
                "the obsolete fluid copy is removed while the legacy payment is retained");
            helper.startSequence().thenSucceed();
        });
    }

    private static void registerDrain(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(3, 4, 3)
            .set(MACHINE.getX(), MACHINE.getY() - 1, MACHINE.getZ(), EIOBlocks.DRAIN.get().defaultBlockState()));
    }

    private static DrainBlockEntity chargedDrain(EnderGameTestHelper helper) {
        var drain = helper.getBlockEntity(MACHINE.getX(), MACHINE.getY(), MACHINE.getZ(), DrainBlockEntity.class);
        helper.insertIntoContainer(MACHINE.getX(), MACHINE.getY(), MACHINE.getZ(), EIOItems.BASIC_CAPACITOR.get(), 1);
        drain.getEnergyStorage().setEnergyStored(CHARGE);
        return drain;
    }

    private static void tick(DrainBlockEntity drain, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            drain.serverTick();
        }
    }
}
