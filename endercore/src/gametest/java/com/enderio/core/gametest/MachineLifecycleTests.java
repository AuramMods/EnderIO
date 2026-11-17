package com.enderio.core.gametest;

import com.enderio.core.common.blockentity.EnderBlockEntity;
import com.enderio.core.common.loot.CopyBlockEntityDataFunction;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.foundation.io.IOConfig;
import com.enderio.enderio.foundation.io.LegacyMachineData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class MachineLifecycleTests {
    private static MachineLifecycleTestContent.StoredEntity create(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().setBlockAndUpdate(pos, MachineLifecycleTestContent.BLOCK.get().defaultBlockState());
        var entity = (MachineLifecycleTestContent.StoredEntity) helper.getLevel().getBlockEntity(pos);
        entity.inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 257));
        var powered = new ItemStack(SerializationTestContent.ITEM.get());
        powered.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new).receiveEnergy(733, false);
        entity.inventory.setStackInSlot(1, powered);
        entity.energy = 12345;
        entity.fluid = new FluidStack(Fluids.WATER, 713);
        entity.fluid.getOrCreateTag().putString("variant", "kept");
        entity.io = IOConfig.of(IOMode.PUSH).withMode(Direction.UP, IOMode.DISABLED);
        entity.redstone = RedstoneControl.NEVER_ACTIVE;
        entity.worldOnly = 93;
        return entity;
    }

    private static MachineLifecycleTestContent.StoredEntity place(GameTestHelper helper, BlockPos pos, ItemStack item) {
        helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        var context = new UseOnContext(helper.getLevel(), null, InteractionHand.MAIN_HAND, item,
            new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false));
        var result = ((BlockItem) item.getItem()).place(new BlockPlaceContext(context));
        helper.assertTrue(result.consumesAction(), "Native BlockItem placement failed");
        return (MachineLifecycleTestContent.StoredEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static void assertStorage(GameTestHelper helper, MachineLifecycleTestContent.StoredEntity entity) {
        helper.assertTrue(entity.inventory.getStackInSlot(0).getCount() == 257, "Inventory count changed through lifecycle");
        helper.assertTrue(entity.inventory.getStackInSlot(1).getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new).getEnergyStored() == 733,
            "Nested item capability was lost");
        helper.assertTrue(entity.fluid.getAmount() == 713 && entity.fluid.getTag().getString("variant").equals("kept"), "Fluid data changed");
        helper.assertTrue(entity.io.getMode(Direction.UP) == IOMode.DISABLED && entity.io.getMode(Direction.NORTH) == IOMode.PUSH,
            "Side configuration changed");
        helper.assertTrue(entity.redstone == RedstoneControl.NEVER_ACTIVE, "Redstone control changed");
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeLootDropAndBlockItemPlacementPreserveCarriedState(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var entity = create(helper, pos);
        var drops = Block.getDrops(entity.getBlockState(), helper.getLevel(), pos, entity);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(MachineLifecycleTestContent.ITEM.get()), "Native loot table did not produce machine item");
        var item = drops.get(0);
        helper.assertTrue(BlockItem.getBlockEntityData(item) == null, "Ordinary loot copied world-only NBT");
        var placed = place(helper, pos, ItemStack.of(item.save(new CompoundTag())));
        assertStorage(helper, placed);
        helper.assertTrue(placed.itemReads == 1 && placed.energySeenByPlacement == 12345 && placed.worldOnly == 0,
            "Item data was not applied once before placement or copied world-only state");
        placed.inventory.getStackInSlot(0).shrink(1);
        helper.assertTrue(entity.inventory.getStackInSlot(0).getCount() == 257, "Placed inventory aliases source block");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeSaveToItemSeparatesWorldNbtFromMutableCarriedData(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var original = create(helper, pos);
        var item = new ItemStack(MachineLifecycleTestContent.ITEM.get());
        original.saveToItem(item);
        var custom = BlockItem.getBlockEntityData(item);
        helper.assertTrue(custom.getInt("WorldOnly") == 93 && !custom.contains("Energy") && !custom.contains("Items"),
            "Full item save duplicated carried state or lost world state");
        MachineLifecycleTestContent.ENERGY.set(item, 42);
        var restored = place(helper, pos, item);
        assertStorage(helper, restored);
        helper.assertTrue(restored.energy == 42 && restored.energySeenByPlacement == 42 && restored.worldOnly == 93,
            "Stale block NBT overrode changed item data or lost full-save state");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void legacyBlockEntityTagPlacementIsNotResetByMissingItemData(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var original = create(helper, pos);
        var item = new ItemStack(MachineLifecycleTestContent.ITEM.get());
        BlockItem.setBlockEntityData(item, original.getType(), original.saveWithoutMetadata());
        var restored = place(helper, pos, item);
        assertStorage(helper, restored);
        helper.assertTrue(restored.energy == 12345 && restored.worldOnly == 93 && restored.itemReads == 0,
            "Missing carried keys reset native BlockEntityTag data");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void removedCarriedFluidDoesNotReappearFromFullItemSave(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var original = create(helper, pos);
        var item = new ItemStack(MachineLifecycleTestContent.ITEM.get());
        original.saveToItem(item);
        MachineLifecycleTestContent.FLUID.remove(item);
        var restored = place(helper, pos, item);
        helper.assertTrue(restored.fluid.isEmpty() && restored.inventory.getStackInSlot(0).getCount() == 257
            && restored.energy == 12345, "Removed fluid reappeared or affected unrelated carried resources");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeBlockEntitySaveAndLoadStaticDispatchStorageHooks(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var original = create(helper, pos);
        var tag = original.saveWithFullMetadata();
        var restored = (MachineLifecycleTestContent.StoredEntity) BlockEntity.loadStatic(pos, original.getBlockState(), tag);
        helper.assertTrue(restored != null, "Native block entity factory failed to reload");
        helper.getLevel().removeBlockEntity(pos);
        helper.getLevel().setBlockEntity(restored);
        assertStorage(helper, restored);
        helper.assertTrue(restored.energy == 12345 && restored.worldOnly == 93 && restored.getLevel() == helper.getLevel(),
            "World load failed to restore state or attach the level");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lootSelectionRoundTripsNativeJsonAndKeepsUnselectedValues(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var entity = create(helper, pos);
        var gson = Deserializers.createFunctionSerializer().create();
        var function = CopyBlockEntityDataFunction.copyOnly(MachineLifecycleTestContent.ENERGY.id()).build();
        var roundTrip = gson.fromJson(gson.toJson(function, LootItemFunction.class), LootItemFunction.class);
        var params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.BLOCK_STATE, entity.getBlockState()).withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
            .withParameter(LootContextParams.BLOCK_ENTITY, entity).create(LootContextParamSets.BLOCK);
        var context = new LootContext.Builder(params).create(null);
        var item = new ItemStack(MachineLifecycleTestContent.ITEM.get());
        item.getOrCreateTag().putString("unrelated", "kept");
        MachineLifecycleTestContent.ENERGY.remove(item);
        roundTrip.apply(item, context);
        helper.assertTrue(MachineLifecycleTestContent.ENERGY.get(item) == 12345 && !EnderBlockEntity.ITEM_INVENTORY.has(item)
            && item.getTag().getString("unrelated").equals("kept"), "Include selection or native removed-key override failed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void historicalIoAndRedstoneFormatsMigrateWithoutChangingSource(GameTestHelper helper) {
        var old = new CompoundTag();
        for (Direction side : Direction.values()) old.putByte(Integer.toString(side.get3DDataValue()), (byte) (side.get3DDataValue() % 5));
        var config = IOConfig.parseOptional(old);
        for (Direction side : Direction.values()) helper.assertTrue(config.getMode(side) == IOMode.BY_ID.apply(side.get3DDataValue() % 5), "Legacy side mapping changed");
        helper.assertTrue(IOConfig.parse(config.save()).orElseThrow().equals(config), "Converted config failed native round trip");
        for (int i = 0; i < 4; i++) helper.assertTrue(RedstoneControl.parse(IntTag.valueOf(i)) == RedstoneControl.BY_ID.apply(i), "Legacy redstone ordinal changed");
        var malformed = old.copy(); malformed.putByte("0", (byte) 9);
        helper.assertTrue(IOConfig.parse(malformed).isEmpty(), "Unknown legacy mode accepted");
        try { RedstoneControl.parse(IntTag.valueOf(4)); throw new AssertionError("Unknown redstone mode accepted"); }
        catch (IllegalArgumentException expected) {}
        var source = new CompoundTag();
        var attachments = new CompoundTag();
        attachments.put("enderio:io_config", config.save());
        attachments.put("enderio:redstone_control", RedstoneControl.NEVER_ACTIVE.save());
        attachments.putString("another_mod:data", "keep");
        var range = new CompoundTag();
        range.putInt("range", 7);
        range.putBoolean("isVisible", true);
        attachments.put("enderio:action_range", range);
        source.put("neoforge:attachments", attachments);
        source.put("RedstoneControl", RedstoneControl.ALWAYS_ACTIVE.save());
        var migrated = LegacyMachineData.withNativeFields(source);
        helper.assertTrue(!source.contains("IOConfig") && migrated.getCompound("IOConfig").equals(config.save())
            && RedstoneControl.parse(migrated.get("RedstoneControl")) == RedstoneControl.ALWAYS_ACTIVE
            && migrated.getCompound("neoforge:attachments").getString("another_mod:data").equals("keep"),
            "Migration mutated source, overrode native state or removed unrelated data");
        helper.assertTrue(migrated.getCompound("ActionRange").getInt("range") == 7 && migrated.getBoolean("IsRangeVisible"),
            "Legacy range or visibility was lost");
        helper.succeed();
    }
}
