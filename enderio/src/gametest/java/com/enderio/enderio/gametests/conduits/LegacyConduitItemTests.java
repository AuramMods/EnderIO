package com.enderio.enderio.gametests.conduits;

import com.enderio.core.common.serialization.StackDataCodecs;
import com.enderio.enderio.api.EnderIODataComponents;
import com.enderio.enderio.content.conduits.ConduitBlockItem;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.content.filters.fluid.EnderFluidFilter;
import com.enderio.enderio.content.filters.item.general.DamageFilterMode;
import com.enderio.enderio.content.filters.item.general.EnderItemFilter;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public class LegacyConduitItemTests {
    @GameTest
    public static void allSixMigratedCoreConduitItemsPlaceTheirTypes(GameplayTest test) {
        var types = List.of("item", "energy", "redstone", "fluid", "pressurized_fluid", "ender_fluid");
        test.registerGameTestTemplate(() -> {
            var fixture = FixtureBuilder.withSize(types.size() * 2 - 1, 2, 1);
            for (int i = 0; i < types.size(); i++) fixture.set(i * 2, 0, 0, Blocks.STONE.defaultBlockState());
            return fixture;
        });
        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence().thenExecute(() -> {
            var level = helper.getLevel();
            var player = FakePlayerFactory.getMinecraft(level);
            for (int i = 0; i < types.size(); i++) {
                String type = types.get(i);
                int count = i + 3;
                var saved = oldStack(type + "_conduit", count);
                var before = saved.copy();
                var stack = ItemStack.of(saved);
                var id = ResourceLocation.fromNamespaceAndPath("enderio", type);
                var conduit = EnderIODataComponents.CONDUIT.get(stack, level.registryAccess());
                if (!stack.is(EIOItems.CONDUIT.get()) || stack.getCount() != count || !id.equals(EnderIODataComponents.CONDUIT.getId(stack))
                    || conduit == null || !saved.equals(before) || stack.getTag().getInt("LegacyFixture") != 17) {
                    throw new GameTestAssertException("Conduit item migration failed for " + type);
                }
                var floor = helper.absolutePos(new BlockPos(i * 2, 1, 0));
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                var hit = new BlockHitResult(Vec3.atBottomCenterOf(floor.above()), Direction.UP, floor, false);
                var result = ((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
                if (!result.consumesAction() || !(level.getBlockEntity(floor.above()) instanceof ConduitBundleBlockEntity bundle)
                    || !bundle.hasConduitStrict(conduit) || stack.getCount() != count - 1) {
                    throw new GameTestAssertException("Migrated item placed the wrong conduit or changed its count for " + type);
                }
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }).thenSucceed());
    }

    @GameTest
    public static void migrationRetainsExistingTypesAndOptionalIds(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1));
        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence().thenExecute(() -> {
            var redstone = ResourceLocation.parse("enderio:redstone");
            var existing = ConduitBlockItem.getStackFor(redstone, 5).save(new CompoundTag());
            existing.putString("id", "enderio:energy_conduit");
            var migrated = ItemStack.of(existing);
            if (!redstone.equals(EnderIODataComponents.CONDUIT.getId(migrated))) {
                throw new GameTestAssertException("The old registry key replaced explicit current type data");
            }

            var oldStorage = oldStack("refined_storage_conduit", 1);
            oldStorage.putInt("Count", 137);
            var rs = ResourceLocation.parse("enderio:rs");
            var storage = StackDataCodecs.loadItem(oldStorage);
            var reopened = StackDataCodecs.loadItem(StackDataCodecs.saveItem(storage));
            if (!storage.is(EIOItems.CONDUIT.get()) || !rs.equals(EnderIODataComponents.CONDUIT.getId(storage))
                || !rs.equals(EnderIODataComponents.CONDUIT.getId(reopened)) || reopened.getCount() != 137) {
                throw new GameTestAssertException("The strict codec lost an optional type or an integer count");
            }
        }).thenSucceed());
    }

    @GameTest
    public static void migratedTransferFiltersKeepForge6Matching(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1));
        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence().thenExecute(() -> {
            var axe = new ItemStack(Items.IRON_AXE);
            axe.setDamageValue(12);
            var differentDamage = axe.copy();
            differentDamage.setDamageValue(13);
            var modernItem = new EnderItemFilter(List.of(axe), false, true, DamageFilterMode.IGNORE);
            var legacyItem = ItemStack.of(oldFilter("advanced_item_filter", "ItemEntries", axe.save(new CompoundTag()), true));
            var reopenedItem = ItemStack.of(legacyItem.save(new CompoundTag()));
            var restoredItem = EIODataComponents.ITEM_FILTER.getOrDefault(reopenedItem, EnderItemFilter.EMPTY);
            if (restoredItem.test(null, axe).isEmpty() || !restoredItem.test(null, differentDamage).isEmpty()
                || modernItem.test(null, differentDamage).isEmpty()) {
                throw new GameTestAssertException("Forge 6 exact item matching did not retain the saved damage rule");
            }

            var water = new FluidStack(Fluids.WATER, 37);
            water.getOrCreateTag().putInt("Sample", 7);
            var differentFluid = new FluidStack(Fluids.LAVA, 91, water.getTag().copy());
            var differentTag = new FluidStack(Fluids.WATER, 53);
            differentTag.getOrCreateTag().putInt("Sample", 8);
            var untagged = new FluidStack(Fluids.LAVA, 40);
            var modernFluid = new EnderFluidFilter(List.of(water), false, true);
            var legacyFluid = ItemStack.of(oldFilter("basic_fluid_filter", "FluidEntries",
                water.writeToNBT(new CompoundTag()), true));
            var restoredFluid = EIODataComponents.FLUID_FILTER.getOrDefault(
                ItemStack.of(legacyFluid.save(new CompoundTag())), EnderFluidFilter.EMPTY);
            if (restoredFluid.test(null, differentFluid).isEmpty() || restoredFluid.test(null, untagged).isEmpty()
                || !restoredFluid.test(null, differentTag).isEmpty() || !modernFluid.test(null, differentFluid).isEmpty()) {
                throw new GameTestAssertException("Forge 6 fluid tag matching or empty sample behavior was changed");
            }

            var legacyIdMode = ItemStack.of(oldFilter("basic_fluid_filter", "FluidEntries",
                water.writeToNBT(new CompoundTag()), false));
            var restoredIdMode = EIODataComponents.FLUID_FILTER.getOrDefault(legacyIdMode, EnderFluidFilter.EMPTY);
            if (restoredIdMode.test(null, water.copy()).isEmpty() || !restoredIdMode.test(null, differentTag).isEmpty()
                || !restoredIdMode.test(null, differentFluid).isEmpty()) {
                throw new GameTestAssertException("Forge 6 fluid ID matching did not preserve the saved tag");
            }
        }).thenSucceed());
    }

    @GameTest
    public static void unavailableTransferFilterSamplesKeepOriginalPayload(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1));
        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence().thenExecute(() -> {
            var missingItem = new CompoundTag();
            missingItem.putString("id", "absent_mod:sample");
            missingItem.putByte("Count", (byte) 1);
            var savedItem = oldFilter("advanced_item_filter", "ItemEntries", missingItem, true);
            var item = ItemStack.of(savedItem);
            var missingFluid = new CompoundTag();
            missingFluid.putString("FluidName", "absent_mod:sample");
            missingFluid.putInt("Amount", 1000);
            var savedFluid = oldFilter("basic_fluid_filter", "FluidEntries", missingFluid, true);
            var fluid = ItemStack.of(savedFluid);
            if (item.isEmpty() || fluid.isEmpty() || !savedItem.equals(item.save(new CompoundTag()))
                || !savedFluid.equals(fluid.save(new CompoundTag())) || EIODataComponents.ITEM_FILTER.has(item)
                || EIODataComponents.FLUID_FILTER.has(fluid)) {
                throw new GameTestAssertException("An unavailable sample discarded or altered the original filter data");
            }

            var canonical = new EnderItemFilter(List.of(new ItemStack(Items.GOLD_INGOT)), true, false, DamageFilterMode.IGNORE);
            EIODataComponents.ITEM_FILTER.set(item, canonical);
            var reopened = ItemStack.of(item.save(new CompoundTag()));
            if (!canonical.equals(EIODataComponents.ITEM_FILTER.get(reopened))) {
                throw new GameTestAssertException("The old payload overrode an explicit current filter value");
            }
        }).thenSucceed());
    }

    private static CompoundTag oldFilter(String item, String entriesKey, CompoundTag sample, boolean compareTags) {
        var saved = oldStack(item, 1);
        var tag = saved.getCompound("tag");
        var entries = new ListTag();
        entries.add(new CompoundTag());
        entries.add(sample);
        tag.put(entriesKey, entries);
        tag.putBoolean("IsNbt", compareTags);
        tag.putBoolean("IsInverted", false);
        return saved;
    }

    private static CompoundTag oldStack(String name, int count) {
        var saved = new CompoundTag();
        saved.putString("id", "enderio:" + name);
        saved.putByte("Count", (byte) count);
        var tag = new CompoundTag();
        tag.putInt("LegacyFixture", 17);
        saved.put("tag", tag);
        return saved;
    }
}
