package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.gametests.EnderIOTests;
import com.enderio.core.common.energy.ItemStackEnergy;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorBankBlockEntity;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorBankMenu;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorTier;
import com.enderio.enderio.content.machines.capacitor_bank.DisplayMode;
import com.enderio.enderio.foundation.network.MachinePayloadHandler;
import com.enderio.enderio.foundation.network.packets.ServerboundCycleIOConfigPacket;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.gametests.util.FixtureTemplate;
import com.enderio.enderio.gametests.util.FixtureBuilder;

import java.util.UUID;
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

    private static ItemStack assertBankDrop(EnderGameTestHelper helper, BlockPos pos, int charge) {
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
            new AABB(helper.absolutePos(pos)).inflate(1.0),
            entity -> entity.getItem().is(EIOBlocks.CAPACITOR_BANKS.get(CapacitorTier.BASIC).get().asItem()));
        helper.assertValueEqual(drops.size(), 1, "one removed bank item");
        var drop = drops.get(0);
        var stack = drop.getItem();
        helper.assertValueEqual(ItemStackEnergy.getEnergyStored(stack), charge, "bank drop charge");
        drop.discard();
        return stack;
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

    @GameTest
    public static void unsetFacesPowerAdjacentMachines(GameplayTest test) {
        var bankBlock = EIOBlocks.CAPACITOR_BANKS.get(CapacitorTier.BASIC).get();
        var charger = EIOBlocks.WIRED_CHARGER.get().defaultBlockState();
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(2, 2, 1)
            .set(0, 0, 0, bankBlock.defaultBlockState())
            .set(0, 1, 0, charger)
            .set(1, 0, 0, charger));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var bank = helper.getBlockEntity(0, 1, 0, CapacitorBankBlockEntity.class);
            var input = bank.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).orElseThrow(AssertionError::new);
            helper.startSequence().thenExecute(() -> {
                helper.insertIntoContainer(0, 2, 0, EIOItems.BASIC_CAPACITOR.get(), 1);
                helper.insertIntoContainer(1, 1, 0, EIOItems.BASIC_CAPACITOR.get(), 1);
                helper.assertValueEqual(bank.getIOMode(Direction.UP), IOMode.BOTH, "unset upper face");
                helper.assertValueEqual(bank.getIOMode(Direction.EAST), IOMode.BOTH, "unset horizontal face");
                helper.assertValueEqual(bank.getIOOverlayMode(Direction.EAST), IOMode.NONE, "unset face stays unmarked");
                helper.assertValueEqual(input.receiveEnergy(2048, false), 2048, "unset face accepts an external charge");
            }).thenExecuteAfter(3, () -> {
                helper.assertEnergyStored(0, 2, 0, 1024);
                helper.assertEnergyStored(1, 1, 0, 1024);
                helper.assertEnergyStored(0, 1, 0, 0);
                bank.setIOMode(Direction.UP, IOMode.NONE);
                bank.setIOMode(Direction.EAST, IOMode.PULL);
                helper.provideEnergyExact(0, 1, 0, 2048);
            }).thenExecuteAfter(3, () -> {
                helper.assertEnergyStored(0, 2, 0, 1024);
                helper.assertEnergyStored(1, 1, 0, 1024);
                helper.assertEnergyStored(0, 1, 0, 2048);
                bank.setIOMode(Direction.UP, IOMode.PUSH);
                bank.setIOMode(Direction.EAST, IOMode.DISABLED);
                bank.setNetworkRedstoneControl(RedstoneControl.NEVER_ACTIVE);
            }).thenExecuteAfter(3, () -> {
                helper.assertEnergyStored(0, 2, 0, 1024);
                helper.assertEnergyStored(1, 1, 0, 1024);
                helper.assertEnergyStored(0, 1, 0, 2048);
                bank.setNetworkRedstoneControl(RedstoneControl.ALWAYS_ACTIVE);
            }).thenExecuteAfter(3, () -> {
                helper.assertEnergyStored(0, 2, 0, 3072);
                helper.assertEnergyStored(1, 1, 0, 1024);
                helper.assertEnergyStored(0, 1, 0, 0);
            }).thenSucceed();
        });
    }

    @GameTest
    public static void openBankMenuCanEditDistantMembersOfItsOwnNetwork(GameplayTest test) {
        var bankBlock = EIOBlocks.CAPACITOR_BANKS.get(CapacitorTier.BASIC).get().defaultBlockState();
        test.registerGameTestTemplate(() -> {
            var builder = FixtureBuilder.withSize(11, 1, 1);
            for (int i = 0; i <= 8; i++) {
                builder.set(i, 0, 0, bankBlock);
            }
            return builder.set(10, 0, 0, bankBlock);
        });
        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence().thenExecute(() -> {
            var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "CapBankFaces"));
            player.setGameMode(GameType.SURVIVAL);
            var opened = helper.getBlockEntity(0, 1, 0, CapacitorBankBlockEntity.class);
            var connected = helper.getBlockEntity(8, 1, 0, CapacitorBankBlockEntity.class);
            var separate = helper.getBlockEntity(10, 1, 0, CapacitorBankBlockEntity.class);
            var stand = Vec3.atCenterOf(opened.getBlockPos().south());
            player.moveTo(stand.x, stand.y, stand.z, 0, 0);
            var menu = new CapacitorBankMenu(1, player.getInventory(), opened);
            player.containerMenu = menu;
            helper.assertTrue(menu.stillValid(player), "the opened bank remains in menu reach");
            helper.assertTrue(!player.canReach(connected.getBlockPos(), 1),
                "the selected connected bank is outside ordinary block reach");

            var handler = MachinePayloadHandler.Server.getInstance();
            var packet = new ServerboundCycleIOConfigPacket(connected.getBlockPos(), Direction.SOUTH);
            handler.handleCycleIOConfigPacket(packet, player);
            helper.assertValueEqual(connected.getIOMode(Direction.SOUTH), IOMode.DISABLED, "click cycles the distant member");
            helper.assertValueEqual(connected.getIOOverlayMode(Direction.SOUTH), IOMode.DISABLED, "configured face is marked");
            helper.assertValueEqual(connected.getIOMode(Direction.NORTH), IOMode.BOTH, "the opposite face keeps its mode");
            handler.handleCycleIOConfigPacket(new ServerboundCycleIOConfigPacket(separate.getBlockPos(), Direction.SOUTH), player);
            helper.assertValueEqual(separate.getIOMode(Direction.SOUTH), IOMode.BOTH, "a separate bank cannot be edited remotely");

            player.containerMenu = player.inventoryMenu;
            handler.handleCycleIOConfigPacket(packet, player);
            helper.assertValueEqual(connected.getIOMode(Direction.SOUTH), IOMode.DISABLED, "a closed menu cannot authorize remote edits");
        }).thenSucceed());
    }

    @GameTest
    public static void legacyCarryChargeSurvivesCapabilityAndPlacement(GameplayTest test) {
        var bank = EIOBlocks.CAPACITOR_BANKS.get(CapacitorTier.BASIC).get();
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(7, 2, 1)
            .set(0, 0, 0, Blocks.STONE.defaultBlockState())
            .set(3, 0, 0, Blocks.STONE.defaultBlockState())
            .set(6, 0, 0, Blocks.STONE.defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence().thenExecute(() -> {
            var stack = new ItemStack(bank, 2);
            var legacy = new CompoundTag();
            var energy = new CompoundTag();
            energy.putInt("EnergyStored", 78912);
            legacy.put("Energy", energy);
            var sides = new CompoundTag();
            sides.putByte("2", (byte) 4);
            sides.putByte("3", (byte) 1);
            legacy.put("IoConfig", sides);
            var displays = new CompoundTag();
            displays.putInt("east", DisplayMode.IO.ordinal());
            legacy.put("displaymodes", displays);
            legacy.putInt("RedstoneControl", 2);
            stack.addTagElement("BlockEntityTag", legacy);

            var original = stack.getTag().copy();
            helper.assertValueEqual(ItemStackEnergy.getEnergyStored(stack), 78912, "legacy per-item charge");
            helper.assertValueEqual(ItemStackEnergy.receiveEnergy(stack, 20001, true), 20000, "whole stack simulation");
            helper.assertValueEqual(stack.getTag(), original, "simulation leaves historical NBT intact");

            var fromLegacy = placeBank(helper, 0, stack);
            helper.assertValueEqual(stack.getCount(), 1, "placement consumes one bank");
            helper.assertValueEqual(fromLegacy.getNetwork().getTotalEnergyStored(), 78912L, "legacy charge restored once");
            helper.assertValueEqual(fromLegacy.getIOMode(Direction.NORTH), IOMode.DISABLED, "legacy disabled face");
            helper.assertValueEqual(fromLegacy.getIOMode(Direction.SOUTH), IOMode.PUSH, "legacy push face");
            helper.assertValueEqual(fromLegacy.getDisplayMode(Direction.EAST), DisplayMode.IO, "legacy display face");
            helper.assertValueEqual(fromLegacy.getNetwork().getRedstoneControl(), RedstoneControl.ACTIVE_WITHOUT_SIGNAL,
                "legacy redstone control");

            helper.assertValueEqual(ItemStackEnergy.receiveEnergy(stack, 10001, false), 10001, "recharge existing balance");
            helper.assertValueEqual(ItemStackEnergy.getEnergyStored(stack), 88913, "new balance includes legacy charge");
            helper.assertTrue(!BlockItem.getBlockEntityData(stack).contains("Energy"), "successful transfer retires old charge");
            var fromRecharged = placeBank(helper, 3, stack);
            helper.assertValueEqual(fromRecharged.getNetwork().getTotalEnergyStored(), 88913L, "converted balance restores once");
            helper.assertValueEqual(fromRecharged.getIOMode(Direction.SOUTH), IOMode.PUSH, "recharge preserves legacy side data");

            var dropPos = new BlockPos(3, 2, 0);
            helper.getLevel().destroyBlock(helper.absolutePos(dropPos), true);
            var drop = assertBankDrop(helper, dropPos, 88913);
            var relocated = placeBank(helper, 6, drop);
            helper.assertValueEqual(relocated.getNetwork().getTotalEnergyStored(), 88913L, "native drop restores its full charge");
            helper.assertValueEqual(relocated.getIOMode(Direction.SOUTH), IOMode.PUSH, "native drop restores its side setting");
        }).thenSucceed());
    }

    private static CapacitorBankBlockEntity placeBank(EnderGameTestHelper helper, int x, ItemStack stack) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        var floor = helper.absolutePos(new BlockPos(x, 1, 0));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(floor.above()), Direction.UP, floor, false);
        var result = ((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(result.consumesAction(), "native bank placement succeeds");
        return helper.getBlockEntity(x, 2, 0, CapacitorBankBlockEntity.class);
    }
}
