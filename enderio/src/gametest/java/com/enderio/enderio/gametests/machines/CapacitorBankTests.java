package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.gametests.EnderIOTests;
import com.enderio.core.common.energy.ItemStackEnergy;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorBankBlockEntity;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorTier;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraft.world.phys.AABB;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.gametests.util.FixtureTemplate;
import com.enderio.enderio.gametests.util.FixtureBuilder;

import java.util.function.Supplier;

// Scenario group: machines.capacitor_bank
public class CapacitorBankTests {

    private static final String DOUBLE_CAPACITOR_BANKS = EnderIOTests.MOD_ID + ":double_capacitor_banks";
    private static final String SPLIT_CAPACITOR_BANKS = EnderIOTests.MOD_ID + ":split_capacitor_banks";

    // @formatter:off
    @FixtureTemplate(DOUBLE_CAPACITOR_BANKS)
    public static final Supplier<StructureTemplate> DOUBLE_CAPACITOR_BANKS_TEMPLATE = FixtureBuilder.lazy(1, 2, 1,
        builder -> builder
            .set(0, 0, 0, EIOBlocks.CAPACITOR_BANKS.get(CapacitorTier.BASIC).get().defaultBlockState())
            .set(0, 1, 0, EIOBlocks.CAPACITOR_BANKS.get(CapacitorTier.BASIC).get().defaultBlockState())
    );

    @FixtureTemplate(SPLIT_CAPACITOR_BANKS)
    public static final Supplier<StructureTemplate> SPLIT_CAPACITOR_BANKS_TEMPLATE = FixtureBuilder.lazy(1, 3, 1,
        builder -> builder
            .set(0, 0, 0, EIOBlocks.CAPACITOR_BANKS.get(CapacitorTier.BASIC).get().defaultBlockState())
            .set(0, 2, 0, EIOBlocks.CAPACITOR_BANKS.get(CapacitorTier.BASIC).get().defaultBlockState())
    );
    // @formatter:on

    @GameTest
    // Tests that the capacitor bank can have power.
    public static void testSingleCapBank(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.CAPACITOR_BANKS.get(CapacitorTier.BASIC).get().defaultBlockState()));

        test.onGameTest(EnderGameTestHelper.class, helper -> {
            helper.startSequence()
                .thenExecute(() -> helper.provideEnergy(0,1,0, CapacitorTier.BASIC.getStorageCapacity() / 2))
                .thenExecuteAfter(20, () -> helper.assertEnergyStored(0,1,0, CapacitorTier.BASIC.getStorageCapacity() / 2)) //Test the energy is stored
                .thenExecute(() -> helper.provideEnergy(0,1,0, CapacitorTier.BASIC.getStorageCapacity()))
                .thenExecuteAfter(20, () -> helper.assertEnergyStored(0,1,0, CapacitorTier.BASIC.getStorageCapacity())) //Test the energy is stored
                .thenSucceed();
        });
    }

    @GameTest(template = DOUBLE_CAPACITOR_BANKS)
    // Tests that the double capacitor bank can have power.
    public static void testDoubleCapBank(final EnderGameTestHelper helper) {
        helper.startSequence()
            .thenExecute(() -> helper.provideEnergy(0,1,0, CapacitorTier.BASIC.getStorageCapacity()))
            .thenExecuteAfter(20, () -> helper.assertEnergyStored(0,1,0, CapacitorTier.BASIC.getStorageCapacity())) //Test the energy is stored
            .thenExecuteAfter(20, () -> helper.assertEnergyStored(0,2,0, CapacitorTier.BASIC.getStorageCapacity())) //Test the energy is stored
            .thenExecute(() -> helper.provideEnergy(0,1,0, CapacitorTier.VIBRANT.getStorageCapacity()))
            .thenExecuteAfter(20, () -> helper.assertEnergyStored(0,1,0, 2 * CapacitorTier.BASIC.getStorageCapacity()))
            .thenExecuteAfter(20, () -> helper.assertEnergyStored(0,2,0, 2 * CapacitorTier.BASIC.getStorageCapacity()))
            .thenSucceed();
    }

    @GameTest(template = DOUBLE_CAPACITOR_BANKS)
    // Tests that that removing a cap bank does not delete the power.
    public static void testRemoveCapBank(final EnderGameTestHelper helper) {
        int capacity = CapacitorTier.BASIC.getStorageCapacity();
        var upperBank = new BlockPos(0, 2, 0);
        helper.startSequence()
            .thenExecute(() -> helper.provideEnergy(0, 1, 0, capacity))
            .thenExecuteAfter(20, () -> helper.assertEnergyStored(0, 1, 0, capacity))
            .thenExecute(() -> {
                helper.getLevel().destroyBlock(helper.absolutePos(upperBank), true);
                assertBankDrop(helper, upperBank, capacity / 2);
                helper.assertEnergyStored(0, 1, 0, capacity / 2);
            })
            .thenExecute(() -> helper.setBlock(upperBank, EIOBlocks.CAPACITOR_BANKS.get(CapacitorTier.BASIC).get().defaultBlockState()))
            .thenExecute(() -> helper.provideEnergy(0, 2, 0, 2 * capacity))
            .thenExecuteAfter(20, () -> helper.assertEnergyStored(0, 1, 0, 2 * capacity))
            .thenExecute(() -> {
                helper.getLevel().destroyBlock(helper.absolutePos(upperBank), true);
                assertBankDrop(helper, upperBank, capacity);
                helper.assertEnergyStored(0, 1, 0, capacity);
            })
            .thenSucceed();
    }

    private static void assertBankDrop(EnderGameTestHelper helper, BlockPos pos, int charge) {
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
            new AABB(helper.absolutePos(pos)).inflate(1.0),
            entity -> entity.getItem().is(EIOBlocks.CAPACITOR_BANKS.get(CapacitorTier.BASIC).get().asItem()));
        helper.assertValueEqual(drops.size(), 1, "one removed bank item");
        helper.assertValueEqual(ItemStackEnergy.getEnergyStored(drops.get(0).getItem()), charge, "bank drop charge");
        drops.get(0).discard();
    }

    @GameTest(template = SPLIT_CAPACITOR_BANKS)
    // Tests that that cap banks can merge
    public static void testMergeCapBank(final EnderGameTestHelper helper) {
        helper.startSequence()
            .thenExecute(() -> helper.provideEnergy(0,1,0, CapacitorTier.BASIC.getStorageCapacity()))
            .thenExecuteAfter(20, () -> helper.assertEnergyStored(0,1,0, CapacitorTier.BASIC.getStorageCapacity())) //Test the energy is stored
            .thenExecute(() -> helper.provideEnergy(0,3,0, CapacitorTier.BASIC.getStorageCapacity()))
            .thenExecuteAfter(20, () -> helper.assertEnergyStored(0,3,0, CapacitorTier.BASIC.getStorageCapacity())) //Test the energy is stored
            .thenExecute(() -> helper.setBlock(new BlockPos(0,2,0), EIOBlocks.CAPACITOR_BANKS.get(CapacitorTier.BASIC).get().defaultBlockState()))
            .thenExecuteAfter(20, () -> helper.assertEnergyStored(0,1,0, 2 * CapacitorTier.BASIC.getStorageCapacity())) //Test the energy is stored
            .thenExecuteAfter(20, () -> helper.assertEnergyStored(0,2,0, 2 * CapacitorTier.BASIC.getStorageCapacity())) //Test the energy is stored
            .thenExecuteAfter(20, () -> helper.assertEnergyStored(0,3,0, 2 * CapacitorTier.BASIC.getStorageCapacity()))
            .thenSucceed();
    }

    @GameTest
    public static void fullBankExportsItsChargeBeforeAndAfterRemoval(GameplayTest test) {
        var bankBlock = EIOBlocks.CAPACITOR_BANKS.get(CapacitorTier.BASIC).get();
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1).set(0, 0, 0, bankBlock.defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence()
            .thenExecute(() -> {
                var pos = helper.absolutePos(new BlockPos(0, 1, 0));
                var level = helper.getLevel();
                var bank = helper.getBlockEntity(0, 1, 0, CapacitorBankBlockEntity.class);
                var state = bank.getBlockState();
                var capability = bank.getCapability(ForgeCapabilities.ENERGY, Direction.SOUTH);
                var storage = capability.orElseThrow(AssertionError::new);
                int capacity = CapacitorTier.BASIC.getStorageCapacity();
                helper.assertValueEqual(storage.receiveEnergy(capacity, false), capacity, "full bank");
                var preparedLoot = Block.getDrops(state, level, pos, bank);
                helper.assertValueEqual(ItemStackEnergy.getEnergyStored(preparedLoot.get(0)), capacity, "loot prepared before removal");
                helper.assertValueEqual(storage.getEnergyStored(), capacity, "preview leaves the live bank untouched");

                helper.setBlock(0, 1, 0, Blocks.AIR);
                var droppedLoot = Block.getDrops(state, level, pos, bank);
                helper.assertValueEqual(ItemStackEnergy.getEnergyStored(droppedLoot.get(0)), capacity, "loot generated after removal");
                helper.assertTrue(!capability.isPresent() && !storage.canReceive() && !storage.canExtract(),
                    "removed bank invalidates the optional and retained storage view");
                helper.assertValueEqual(storage.extractEnergy(capacity, false), 0, "removed view cannot spend the carried charge");
            })
            .thenSucceed());
    }
}
