package com.enderio.core.gametest;

import com.enderio.core.common.capability.CapabilityUtil;
import com.enderio.core.common.capability.NeighborCapability;
import com.enderio.core.common.capability.ItemCapabilityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class CapabilityTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static CapabilityTestBlocks.FixtureBlockEntity place(GameTestHelper helper) {
        helper.setBlock(POS, CapabilityTestBlocks.BLOCK.get());
        return (CapabilityTestBlocks.FixtureBlockEntity) helper.getBlockEntity(POS);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void attachedCapabilitiesRespectSidesAndReuseInstances(GameTestHelper helper) {
        var entity = place(helper);
        var first = entity.getCapability(ForgeCapabilities.ENERGY, Direction.WEST);
        helper.assertTrue(first.orElse(null) == entity.storage, "Attached factory did not expose its energy storage");
        helper.assertTrue(entity.getCapability(ForgeCapabilities.ENERGY, Direction.WEST) == first,
            "Repeated side queries must share an invalidatable optional");
        helper.assertTrue(entity.factoryCalls == 1, "Successful factory was invoked again");
        helper.assertFalse(entity.getCapability(ForgeCapabilities.ENERGY, Direction.EAST).isPresent(), "Closed side exposed energy");
        helper.assertTrue(entity.getCapability(ForgeCapabilities.ENERGY, null).isPresent(), "Unsided access was lost");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unavailableCapabilityIsDiscoveredWhenEnabled(GameTestHelper helper) {
        var entity = place(helper);
        entity.exposed = false;
        var changes = new AtomicInteger();
        try (var neighbor = NeighborCapability.create(ForgeCapabilities.ENERGY, helper.getLevel(),
                helper.absolutePos(POS), Direction.WEST, () -> true, changes::incrementAndGet)) {
            helper.assertTrue(neighbor.getCapability() == null, "Disabled handler was exposed");
            entity.exposed = true;
            neighbor.refresh();
            helper.assertTrue(neighbor.getCapability() == entity.storage, "A cached miss hid the newly available handler");
            helper.assertTrue(changes.get() == 1, "Appearance must invalidate derived connection caches once");
            neighbor.refresh();
            helper.assertTrue(changes.get() == 1, "Unchanged handler repeatedly invalidated its consumer");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void refreshInvalidatesOldOptionalBeforeNotifying(GameTestHelper helper) {
        var entity = place(helper);
        var old = entity.getCapability(ForgeCapabilities.ENERGY, Direction.WEST);
        var unrelated = entity.getCapability(ForgeCapabilities.ITEM_HANDLER);
        var callbacks = new AtomicInteger();
        old.addListener(ignored -> {
            callbacks.incrementAndGet();
            helper.assertFalse(old.isPresent(), "Consumer was notified before its optional was invalidated");
            helper.assertTrue(entity.getCapability(ForgeCapabilities.ENERGY, Direction.WEST).orElse(null) == entity.storage,
                "An invalidation callback saw an outdated handler after data changed");
        });
        entity.storage = new EnergyStorage(2000);
        CapabilityUtil.refresh(entity);
        helper.assertFalse(old.isPresent(), "Old optional survived refresh");
        helper.assertTrue(callbacks.get() == 1, "Invalidation was not delivered exactly once");
        var next = entity.getCapability(ForgeCapabilities.ENERGY, Direction.WEST);
        helper.assertTrue(next != old && next.orElse(null) == entity.storage, "Refresh failed to expose the replacement handler");
        helper.assertTrue(unrelated.isPresent() && entity.getCapability(ForgeCapabilities.ITEM_HANDLER) == unrelated,
            "Block refresh invalidated an unrelated provider");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void removalAndReplacementRefreshNeighbor(GameTestHelper helper) {
        var old = place(helper);
        var changes = new AtomicInteger();
        try (var neighbor = NeighborCapability.create(ForgeCapabilities.ENERGY, helper.getLevel(),
                helper.absolutePos(POS), Direction.WEST, () -> true, changes::incrementAndGet)) {
            helper.assertTrue(neighbor.getCapability() == old.storage, "Initial neighbor missing");
            helper.setBlock(POS, Blocks.AIR);
            helper.assertTrue(changes.get() == 1, "Removal did not notify the neighbor consumer");
            helper.assertTrue(neighbor.getCapability() == null, "Removed neighbor's handler was retained");
            var replacement = place(helper);
            helper.assertTrue(neighbor.getCapability() == replacement.storage, "Replacement was not discovered");
            helper.assertTrue(changes.get() == 2, "Replacement did not invalidate derived caches");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void closingNeighborDetachesListener(GameTestHelper helper) {
        var entity = place(helper);
        var changes = new AtomicInteger();
        var neighbor = NeighborCapability.create(ForgeCapabilities.ENERGY, helper.getLevel(),
            helper.absolutePos(POS), Direction.WEST, () -> true, changes::incrementAndGet);
        helper.assertTrue(neighbor.getCapability() != null, "Initial neighbor missing");
        neighbor.close();
        CapabilityUtil.refresh(entity);
        helper.assertTrue(changes.get() == 0, "Closed cache retained an invalidation listener");
        helper.assertTrue(neighbor.getCapability() == null, "Closed cache reacquired a capability");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void ownerRemovalStopsWatchingNeighbor(GameTestHelper helper) {
        var entity = place(helper);
        var ownerValid = new AtomicBoolean(true);
        var changes = new AtomicInteger();
        try (var neighbor = NeighborCapability.create(ForgeCapabilities.ENERGY, helper.getLevel(),
                helper.absolutePos(POS), Direction.WEST, ownerValid::get, changes::incrementAndGet)) {
            helper.assertTrue(neighbor.getCapability() != null, "Initial neighbor missing");
            ownerValid.set(false);
            neighbor.refresh();
            CapabilityUtil.refresh(entity);
            helper.assertTrue(changes.get() == 0, "Removed owner received a capability callback");
            helper.assertTrue(neighbor.getCapability() == null, "Removed owner retained a live handler");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void queriesDoNotLoadMissingChunks(GameTestHelper helper) {
        var distant = helper.absolutePos(POS).offset(1_000_000, 0, 1_000_000);
        helper.assertFalse(helper.getLevel().hasChunkAt(distant), "Fixture chunk was already loaded");
        try (var neighbor = NeighborCapability.create(ForgeCapabilities.ENERGY, helper.getLevel(),
                distant, null, () -> true, () -> {})) {
            helper.assertTrue(neighbor.getCapability() == null, "Unloaded chunk returned a handler");
            neighbor.refresh();
            helper.assertFalse(helper.getLevel().hasChunkAt(distant), "Capability query forced a chunk to load");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void copiedStacksOwnIndependentProviders(GameTestHelper helper) {
        var original = new ItemStack(CapabilityTestBlocks.ITEM.get());
        original.getOrCreateTag().putInt("energy", 200);
        var first = original.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new);
        var copied = original.copy();
        var second = copied.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new);
        helper.assertTrue(first != second, "Copied stacks shared a capability instance");
        helper.assertTrue(second.getEnergyStored() == 200, "Copied provider read the wrong stack data");
        first.extractEnergy(25, false);
        helper.assertTrue(second.getEnergyStored() == 200, "One stack's handler mutated the copy's handler");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void itemDataRefreshInvalidatesOnlyOwnedProviders(GameTestHelper helper) {
        var stack = new ItemStack(CapabilityTestBlocks.ITEM.get());
        stack.getOrCreateTag().putInt("energy", 200);
        var old = stack.getCapability(ForgeCapabilities.ENERGY);
        var oldFluid = stack.getCapability(ForgeCapabilities.FLUID_HANDLER);
        helper.assertTrue(oldFluid.orElseThrow(IllegalStateException::new).getTankCapacity(0) == 200, "Second provider fixture is incorrect");
        var unrelated = stack.getCapability(ForgeCapabilities.ITEM_HANDLER);
        var callbacks = new AtomicInteger();
        old.addListener(ignored -> {
            callbacks.incrementAndGet();
            helper.assertTrue(stack.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(IllegalStateException::new).getTankCapacity(0) == 500,
                "Callback observed outdated data in another owned provider");
        });
        stack.getOrCreateTag().putInt("energy", 500);
        ItemCapabilityRegistry.refresh(stack);
        helper.assertFalse(old.isPresent(), "Data refresh retained an outdated handler");
        helper.assertFalse(oldFluid.isPresent(), "Second owned provider retained an outdated handler");
        helper.assertTrue(callbacks.get() == 1, "Data refresh did not notify the old consumer");
        helper.assertTrue(stack.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new).getEnergyStored() == 500,
            "New capability did not read the changed stack data");
        helper.assertTrue(unrelated.isPresent() && stack.getCapability(ForgeCapabilities.ITEM_HANDLER) == unrelated,
            "Refreshing Ender IO data invalidated another provider's capability");
        helper.succeed();
    }
}
