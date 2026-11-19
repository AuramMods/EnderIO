package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.content.filters.item.general.DamageFilterMode;
import com.enderio.enderio.content.filters.item.limited.LimitedItemFilter;
import com.enderio.enderio.content.machines.vacuum.VacuumMachineBlockEntity;
import com.enderio.enderio.content.machines.vacuum.chest.VacuumChestBlockEntity;
import com.enderio.enderio.content.machines.vacuum.xp.XPVacuumBlockEntity;
import com.enderio.enderio.foundation.attachment.ActionRange;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOFluids;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public class VacuumTests {
    @GameTest
    public static void stockFilterLeavesUnacceptedItemsInTheWorld(GameplayTest test) {
        template(test, EIOBlocks.VACUUM_CHEST.get());
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(0, 1, 0, VacuumChestBlockEntity.class);
            var filter = new ItemStack(EIOItems.LIMITED_ITEM_FILTER.get());
            var sample = new ItemStack(Items.IRON_INGOT, 4);
            sample.getOrCreateTag().putInt("PortFixture", 135);
            EIODataComponents.LIMITED_ITEM_FILTER.set(filter, new LimitedItemFilter(List.of(sample), true, DamageFilterMode.IGNORE));
            VacuumMachineBlockEntity.FILTER.setStackInSlot(machine, filter);
            machine.getInventory().setStackInSlot(0, sample.copyWithCount(1));
            var offered = new ItemEntity(helper.getLevel(), 0, 80, 0, sample.copyWithCount(10));

            machine.handleEntity(offered);
            helper.assertValueEqual(machine.getInventory().getStackInSlot(0).getCount(), 4, "fill exactly to the stock limit");
            helper.assertTrue(!offered.isRemoved() && ItemStack.matches(offered.getItem(), sample.copyWithCount(7)),
                "the seven rejected tagged items survive in the original entity");
            machine.handleEntity(offered);
            helper.assertValueEqual(offered.getItem().getCount(), 7, "a full filter quota cannot consume another item");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void collectionNeverInstallsAFilterInTheControlSlot(GameplayTest test) {
        template(test, EIOBlocks.VACUUM_CHEST.get());
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(0, 1, 0, VacuumChestBlockEntity.class);
            for (int i = 0; i < VacuumMachineBlockEntity.FILTER.getIndex(); i++) {
                machine.getInventory().setStackInSlot(i, new ItemStack(Items.STONE, 64));
            }
            var filter = new ItemStack(EIOItems.BASIC_ITEM_FILTER.get());
            var offered = new ItemEntity(helper.getLevel(), 0, 80, 0, filter.copy());
            machine.handleEntity(offered);
            helper.assertTrue(VacuumMachineBlockEntity.FILTER.getItemStack(machine).isEmpty(), "only a player can install the filter");
            helper.assertTrue(!offered.isRemoved() && ItemStack.matches(offered.getItem(), filter), "the uncollected filter stays in the world");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void xpVacuumCollectsEveryMemberOfAMergedOrb(GameplayTest test) {
        template(test, EIOBlocks.XP_VACUUM.get());
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(0, 1, 0, XPVacuumBlockEntity.class);
            var orb = mergedOrb(machine, 7, 3);
            machine.handleEntity(orb);
            helper.assertValueEqual(machine.getFluidTank().getFluidAmount(), 420, "three seven-point orbs produce 420 mB");
            helper.assertTrue(orb.isRemoved(), "the paid stack of orbs is removed");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void xpVacuumPreservesTheWholeAndPartialOrbRemainders(GameplayTest test) {
        template(test, EIOBlocks.XP_VACUUM.get());
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(0, 1, 0, XPVacuumBlockEntity.class);
            var tank = machine.getFluidTank();
            var tag = new CompoundTag();
            tag.putInt("PortFixture", 135);
            tank.setFluid(new FluidStack(EIOFluids.XP_JUICE.source().get(), tank.getCapacity() - 47, tag));
            var orb = mergedOrb(machine, 7, 3);
            helper.getLevel().addFreshEntity(orb);
            machine.handleEntity(orb);
            helper.assertValueEqual(tank.getFluidAmount(), tank.getCapacity() - 7, "only two whole XP points fit");
            helper.assertValueEqual(tank.getFluid().getTag(), tag, "the existing XP juice tag stays intact");
            helper.assertValueEqual(orb.getValue(), 7, "the original remaining whole orbs keep their denomination");
            helper.assertValueEqual(count(orb), 2, "two whole orbs remain in their original entity");
            var split = helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(orb.blockPosition()).inflate(1), e -> e != orb);
            helper.assertValueEqual(split.size(), 1, "only the partly collected orb is split off");
            helper.assertValueEqual(split.get(0).getValue(), 5, "five points from the partial orb remain");
            helper.assertValueEqual(count(split.get(0)), 1, "the remainder represents one orb");
            machine.handleEntity(split.get(0));
            helper.assertValueEqual(tank.getFluidAmount(), tank.getCapacity() - 7, "sub-point space cannot mint fluid");
            helper.assertValueEqual(split.get(0).getValue(), 5, "sub-point space cannot consume XP");
            orb.discard();
            split.get(0).discard();
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void bothPlacedVacuumsLoadTheirLegacyRangeAndControls(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(2, 1, 1)
            .set(0, 0, 0, EIOBlocks.VACUUM_CHEST.get().defaultBlockState())
            .set(1, 0, 0, EIOBlocks.XP_VACUUM.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var chest = helper.getBlockEntity(0, 1, 0, VacuumChestBlockEntity.class);
            var xp = helper.getBlockEntity(1, 1, 0, XPVacuumBlockEntity.class);
            for (var machine : List.of(chest, xp)) {
                var old = machine.saveWithoutMetadata();
                old.putInt("Range", 2);
                old.putBoolean("RangeVisible", true);
                old.putInt("RedstoneControl", 3);
                machine.load(old);
                helper.assertValueEqual(machine.getActionRange(), new ActionRange(2, true), "Forge 6 range and visibility");
                helper.assertValueEqual(machine.getRedstoneControl(), RedstoneControl.NEVER_ACTIVE, "Forge 6 redstone control");
            }
            helper.startSequence().thenSucceed();
        });
    }

    private static ExperienceOrb mergedOrb(XPVacuumBlockEntity machine, int value, int count) {
        Vec3 pos = Vec3.atCenterOf(machine.getBlockPos()).add(0, 0.6, 0);
        var orb = new ExperienceOrb(machine.getLevel(), pos.x, pos.y, pos.z, value);
        var saved = new CompoundTag();
        orb.addAdditionalSaveData(saved);
        saved.putInt("Count", count);
        orb.readAdditionalSaveData(saved);
        return orb;
    }

    private static int count(ExperienceOrb orb) {
        var saved = new CompoundTag();
        orb.addAdditionalSaveData(saved);
        return saved.getInt("Count");
    }

    private static void template(GameplayTest test, Block block) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 2, 1).set(0, 0, 0, block.defaultBlockState()));
    }
}
