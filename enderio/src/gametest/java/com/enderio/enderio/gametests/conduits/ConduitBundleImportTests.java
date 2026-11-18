package com.enderio.enderio.gametests.conduits;

import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.api.conduits.connection.ConnectionStatus;
import com.enderio.enderio.init.EIOConduitTypes;
import com.enderio.enderio.init.EIOConduits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

public class ConduitBundleImportTests {
    @GameTest
    public static void setblockImportsNodeDataIntoAnAttachedBundle(GameplayTest test) {
        fixture(test, helper -> {
            helper.placeConduit(helper.getConduit(EIOConduits.FLUID), 0, 1, 0);
            var conduit = helper.getConduit(EIOConduits.FLUID);
            var original = helper.getConduitBundle(0, 1, 0, false);
            var data = original.getConduitNode(conduit).getOrCreateNodeData(EIOConduitTypes.NodeData.FLUID.get());
            var water = new FluidStack(Fluids.WATER, 750);
            water.getOrCreateTag().putInt("Conservation", 42);
            data.retain(Direction.WEST, water);
            var saved = original.saveWithoutMetadata();
            var pos = helper.absolutePos(new BlockPos(2, 1, 0));
            var level = helper.getLevel();
            var source = level.getServer().createCommandSourceStack().withLevel(level);
            var outcome = level.getServer().getCommands().performPrefixedCommand(source,
                "setblock %d %d %d enderio:conduit%s".formatted(pos.getX(), pos.getY(), pos.getZ(), saved));
            helper.assertValueEqual(outcome, 1, "Actual setblock result");
            var imported = helper.getConduitBundle(2, 1, 0, false);
            var node = imported.getConduitNode(conduit);
            helper.assertTrue(node != null && node.isAttachedTo(imported), "Native setblock must create an attached graph node");
            var fluid = node.getNodeData(EIOConduitTypes.NodeData.FLUID.get()).first(Direction.WEST);
            helper.assertTrue(water.isFluidStackIdentical(fluid), "Import must preserve the full tagged fluid batch");
        });
    }

    @GameTest
    public static void liveImportRemovesStaleGraphNodesAndPreservesUnchangedOnes(GameplayTest test) {
        fixture(test, helper -> {
            var fluid = helper.getConduit(EIOConduits.FLUID);
            var energy = helper.getConduit(EIOConduits.ENERGY);
            helper.placeConduit(fluid, 0, 1, 0);
            var bundle = helper.getConduitBundle(0, 1, 0, false);
            bundle.addConduit(energy, null, helper.getPlayer());
            var oldFluid = bundle.getConduitNode(fluid);
            var oldEnergy = bundle.getConduitNode(energy);
            var snapshot = fluidOnly(bundle.saveWithoutMetadata());
            bundle.load(snapshot);
            helper.assertTrue(oldFluid == bundle.getConduitNode(fluid) && oldFluid.isAttachedTo(bundle),
                "Live import must keep unchanged node identity");
            helper.assertTrue(!bundle.hasConduitStrict(energy) && !oldEnergy.isAttachedTo(bundle) && !oldEnergy.isValid(),
                "Removed conduit must leave both bundle and graph");
        });
    }

    @GameTest
    public static void importedDisabledFaceSplitsTheLiveGraph(GameplayTest test) {
        fixture(test, helper -> {
            var conduit = helper.getConduit(EIOConduits.FLUID);
            helper.placeConduit(conduit, 0, 1, 0);
            helper.placeConduit(conduit, 1, 1, 0);
            var left = helper.getConduitBundle(0, 1, 0, false);
            var right = helper.getConduitBundle(1, 1, 0, false);
            var node = left.getConduitNode(conduit);
            helper.assertTrue(node.getNetwork() == right.getConduitNode(conduit).getNetwork(), "Fixture must have one shared graph");
            var snapshot = left.saveWithoutMetadata();
            var sides = snapshot.getList("Connections", Tag.TAG_LIST).getList(0);
            for (int i = 0; i < sides.size(); i++) {
                var side = sides.getCompound(i);
                if (side.getString("Side").equals("east")) side.putString("Status", "disabled");
            }
            left.load(snapshot);
            helper.assertTrue(node == left.getConduitNode(conduit) && node.getNetwork() != right.getConduitNode(conduit).getNetwork(),
                "Import must split the link while retaining the same attached node");
            helper.assertValueEqual(right.getConnectionStatus(conduit, Direction.WEST), ConnectionStatus.DISABLED,
                "Neighbor must reflect the imported disabled connection");
        });
    }

    @GameTest
    public static void liveImportReplacesExplicitNodeDataAndRejectsMalformedData(GameplayTest test) {
        fixture(test, helper -> {
            var conduit = helper.getConduit(EIOConduits.FLUID);
            helper.placeConduit(conduit, 0, 1, 0);
            var bundle = helper.getConduitBundle(0, 1, 0, false);
            var node = bundle.getConduitNode(conduit);
            var dataType = EIOConduitTypes.NodeData.FLUID.get();
            node.getOrCreateNodeData(dataType).retain(Direction.WEST, new FluidStack(Fluids.WATER, 750));
            var snapshot = bundle.saveWithoutMetadata();
            var fluid = snapshot.getList("NodeData", Tag.TAG_COMPOUND).getCompound(0).getCompound("Data")
                .getList("pending", Tag.TAG_COMPOUND).getCompound(0).getCompound("fluid");
            fluid.putInt("Amount", 1500);
            bundle.load(snapshot);
            helper.assertTrue(node == bundle.getConduitNode(conduit) && node.isAttachedTo(bundle),
                "Replacing imported data must retain the attached graph node");
            helper.assertValueEqual(node.getNodeData(dataType).amount(), 1500L, "Imported resource amount must replace, not add");

            var validSave = bundle.saveWithoutMetadata();
            fluid.putString("Amount", "invalid");
            boolean rejected = false;
            try {
                bundle.load(snapshot);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected && validSave.equals(bundle.saveWithoutMetadata()),
                "Malformed live node data must preserve the valid saved state");
        });
    }

    @GameTest
    public static void malformedLiveImportPreservesTheAttachedBundle(GameplayTest test) {
        fixture(test, helper -> {
            var fluid = helper.getConduit(EIOConduits.FLUID);
            helper.placeConduit(fluid, 0, 1, 0);
            var bundle = helper.getConduitBundle(0, 1, 0, false);
            var original = bundle.getConduitNode(fluid);
            original.getOrCreateNodeData(EIOConduitTypes.NodeData.FLUID.get()).retain(Direction.WEST,
                new FluidStack(Fluids.WATER, 750));
            var snapshot = bundle.saveWithoutMetadata();
            ((ListTag) snapshot.get("Conduits")).add(StringTag.valueOf("enderio_tests:missing_conduit"));
            boolean rejected = false;
            try {
                bundle.load(snapshot);
            } catch (RuntimeException expected) {
                rejected = true;
            }
            helper.assertTrue(rejected && bundle.getConduits().size() == 1 && bundle.getConduitNode(fluid) == original
                && original.isAttachedTo(bundle), "Invalid import must leave existing graph unchanged");
            var subsequentSave = bundle.saveWithoutMetadata();
            helper.assertValueEqual(subsequentSave.getList("Conduits", Tag.TAG_STRING).size(), 1,
                "Rejected import cannot poison the next save");
            helper.assertValueEqual(subsequentSave.getList("NodeData", Tag.TAG_COMPOUND).size(), 1,
                "A valid graph's node data must survive a rejected import");
        });
    }

    private static CompoundTag fluidOnly(CompoundTag snapshot) {
        var sides = snapshot.getList("Connections", Tag.TAG_LIST);
        var types = snapshot.getList("Conduits", Tag.TAG_STRING);
        for (int i = types.size() - 1; i >= 0; i--) {
            if (types.getString(i).equals("enderio:fluid")) continue;
            types.remove(i);
            sides.remove(i);
        }
        return snapshot;
    }

    private static void fixture(GameplayTest test, java.util.function.Consumer<ConduitGameTestHelper> body) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(3, 1, 1));
        test.onGameTest(ConduitGameTestHelper.class, helper -> helper.startSequence().thenExecute(() -> body.accept(helper)).thenSucceed());
    }
}
