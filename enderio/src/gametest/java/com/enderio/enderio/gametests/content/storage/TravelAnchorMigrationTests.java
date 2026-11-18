package com.enderio.enderio.gametests.content.storage;

import com.enderio.enderio.content.travel.TravelTargetSavedData;
import com.enderio.enderio.content.travel.travel_anchor.AnchorTravelTarget;
import com.enderio.enderio.content.travel.travel_anchor.TravelAnchorBlockEntity;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

public class TravelAnchorMigrationTests {
    @GameTest
    public static void legacyAndNativeTravelRecordsCanShareAFile(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1));
        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence().thenExecute(() -> {
            var level = helper.getLevel();
            var registries = level.registryAccess();
            var currentPos = new BlockPos(10, 10, 10);
            var legacyPos = new BlockPos(10, 18, 10);
            var current = new AnchorTravelTarget(currentPos, "Current", Items.DIAMOND, true);
            var currentData = new TravelTargetSavedData(registries);
            currentData.setTravelTarget(level, current);
            var data = currentData.save(new CompoundTag());
            var entries = data.getList(TravelTargetSavedData.TARGETS, Tag.TAG_COMPOUND);
            entries.add(legacyAnchor(currentPos, "Older", "minecraft:apple", false));
            entries.add(legacyAnchor(legacyPos, "Legacy Lift", "minecraft:ender_pearl", false));
            var unknown = legacyAnchor(new BlockPos(20, 10, 10), "Addon", "minecraft:air", true);
            unknown.putString("name", "testmod:legacy_target");
            entries.add(unknown);

            var saved = new TravelTargetSavedData(data, registries);
            var old = new AnchorTravelTarget(legacyPos, "Legacy Lift", Items.ENDER_PEARL, false);
            if (!saved.getTravelTarget(currentPos).orElseThrow().equals(current)
                || !saved.getTravelTarget(legacyPos).orElseThrow().equals(old)) {
                throw new GameTestAssertException("Legacy travel state or current record precedence changed");
            }
            var converted = saved.save(new CompoundTag());
            var convertedEntries = converted.getList(TravelTargetSavedData.TARGETS, Tag.TAG_COMPOUND);
            if (convertedEntries.size() != 3 || !convertedEntries.contains(unknown)
                || convertedEntries.stream().filter(tag -> ((CompoundTag) tag).contains("type")).count() != 2) {
                throw new GameTestAssertException("Native conversion discarded unsupported records or rewrote a duplicate");
            }
            var reopened = new TravelTargetSavedData(converted, registries);
            if (!reopened.getTravelTarget(legacyPos).orElseThrow().equals(old)) {
                throw new GameTestAssertException("Converted anchor state failed to reopen");
            }
        }).thenSucceed());
    }

    @GameTest
    public static void travelAnchorGhostIconLoadsBeforeLevelAssignment(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.TRAVEL_ANCHOR.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence().thenExecute(() -> {
            var anchor = helper.getBlockEntity(0, 1, 0, TravelAnchorBlockEntity.class);
            anchor.getInventory().setStackInSlot(0, new ItemStack(Items.COMPASS));
            var restored = BlockEntity.loadStatic(anchor.getBlockPos(), anchor.getBlockState(), anchor.saveWithFullMetadata());
            if (!(restored instanceof TravelAnchorBlockEntity loaded) || loaded.hasLevel()
                || !loaded.getInventory().getStackInSlot(0).is(Items.COMPASS)) {
                throw new GameTestAssertException("Native loading lost the anchor or its ghost icon");
            }
            if (anchor.getIcon() != Items.COMPASS) {
                throw new GameTestAssertException("A live inventory edit did not update the travel icon");
            }
        }).thenSucceed());
    }

    private static CompoundTag legacyAnchor(BlockPos pos, String name, String icon, boolean visible) {
        var target = new CompoundTag();
        target.put("BlockPos", NbtUtils.writeBlockPos(pos));
        target.putString("AnchorName", name);
        target.putString("AnchorIcon", icon);
        target.putBoolean("AnchorVisibility", visible);
        var record = new CompoundTag();
        record.putString("name", "enderio:travel_anchor");
        record.put("data", target);
        return record;
    }
}
