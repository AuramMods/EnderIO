package com.enderio.modded_conduits;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.orientation.OrientationStrategies;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.enderio.enderio.api.EnderIORegistries;
import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.api.conduits.bundle.AddConduitResult;
import com.enderio.enderio.content.conduits.ConduitBlockItem;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.modded_conduits.common.modules.appeng.AE2ConduitsModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("enderio_modded_conduits_tests")
@PrefixGameTestTemplate(false)
public class MEChannelIntegrationTests {
    private static final BlockPos CONTROLLER = new BlockPos(1, 2, 3);
    private static final BlockPos HEAD = CONTROLLER.east();
    private static final BlockPos TAIL = HEAD.east();
    private static final int FIRST_BRANCH = 4;
    private static final int LAST_BRANCH = 11;
    private static final int SETTLE_TICKS = 70;

    @GameTest(template = "storage_channels", timeoutTicks = 330)
    public static void meRouteUsesTheNarrowestTierAndReassignsAvailableChannels(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(EnderIORegistries.Keys.CONDUIT);
        var normal = registry.getHolderOrThrow(AE2ConduitsModule.NORMAL);
        var dense = registry.getHolderOrThrow(AE2ConduitsModule.DENSE);
        var drives = drivePositions();
        var waitingDrive = new AtomicReference<IGridNode>();

        helper.startSequence()
            .thenExecute(() -> {
                helper.setBlock(CONTROLLER.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
                helper.setBlock(CONTROLLER, AEBlocks.CONTROLLER.block());
                for (var drive : drives) {
                    // Drives do not expose their grid node on the front face.
                    var facing = drive.getZ() > CONTROLLER.getZ() ? Direction.SOUTH : Direction.NORTH;
                    helper.setBlock(drive, OrientationStrategies.full().setFacing(AEBlocks.DRIVE.block().defaultBlockState(), facing));
                }
            })
            .thenIdle(5)
            .thenExecute(() -> {
                ConduitNetworkIntegrationTests.place(helper, normal, HEAD);
                ConduitNetworkIntegrationTests.place(helper, normal, TAIL);
                for (int x = FIRST_BRANCH; x <= LAST_BRANCH; x++) {
                    ConduitNetworkIntegrationTests.place(helper, dense, branch(x));
                }
            })
            .thenIdle(SETTLE_TICKS)
            .thenExecute(() -> assertChannels(helper, 8, 8, 8, 33))
            .thenExecute(() -> upgrade(helper, HEAD, normal, dense))
            .thenIdle(SETTLE_TICKS)
            .thenExecute(() -> assertChannels(helper, 32, 8, 8, 33))
            .thenExecute(() -> upgrade(helper, TAIL, normal, dense))
            .thenIdle(SETTLE_TICKS)
            .thenExecute(() -> {
                assertChannels(helper, 32, 32, 32, 33);
                var activeDrive = drives.stream().filter(pos -> driveNode(helper, pos).isActive()).findFirst().orElseThrow();
                waitingDrive.set(drives.stream().map(pos -> driveNode(helper, pos)).filter(node -> !node.isActive()).findFirst().orElseThrow());
                helper.setBlock(activeDrive, Blocks.AIR);
            })
            .thenIdle(SETTLE_TICKS)
            .thenExecute(() -> {
                assertChannels(helper, 32, 32, 32, 32);
                var waiting = waitingDrive.get();
                helper.assertTrue(waiting.getGrid() == node(helper, CONTROLLER).getGrid() && waiting.isActive(),
                    "The waiting drive never acquired the freed channel");
            })
            .thenSucceed();
    }

    private static List<BlockPos> drivePositions() {
        var drives = new ArrayList<BlockPos>();
        for (int x = FIRST_BRANCH; x <= LAST_BRANCH; x++) {
            var branch = branch(x);
            for (var direction : List.of(Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN)) {
                drives.add(branch.relative(direction));
            }
        }
        drives.add(branch(LAST_BRANCH).east());
        return drives;
    }

    private static BlockPos branch(int x) {
        return new BlockPos(x, CONTROLLER.getY(), CONTROLLER.getZ());
    }

    private static void upgrade(GameTestHelper helper, BlockPos pos, Holder<Conduit<?, ?>> oldTier, Holder<Conduit<?, ?>> newTier) {
        var bundle = (ConduitBundleBlockEntity) helper.getBlockEntity(pos);
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var result = bundle.addConduitFromItem(newTier, Direction.WEST, player, ConduitBlockItem.getStackFor(newTier, 1));
        helper.assertTrue(result instanceof AddConduitResult.Upgrade replacement
            && replacement.replacedConduit() == oldTier && bundle.hasConduitStrict(newTier) && !bundle.hasConduitStrict(oldTier),
            "Native use did not replace the normal ME conduit at " + pos);
    }

    private static IGridNode node(GameTestHelper helper, BlockPos pos) {
        var node = GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(pos), Direction.EAST);
        helper.assertTrue(node != null, "No exposed ME node at " + pos);
        return node;
    }

    private static IGridNode driveNode(GameTestHelper helper, BlockPos pos) {
        var drive = (DriveBlockEntity) helper.getBlockEntity(pos);
        var node = drive.getMainNode().getNode();
        helper.assertTrue(node != null, "No native ME drive node at " + pos);
        return node;
    }

    private static void assertChannels(GameTestHelper helper, int headCapacity, int tailCapacity, int expectedActive, int expectedDrives) {
        var controller = node(helper, CONTROLLER);
        var head = node(helper, HEAD);
        var tail = node(helper, TAIL);
        var grid = controller.getGrid();
        helper.assertTrue(head.getGrid() == grid && tail.getGrid() == grid, "The conduit route disconnected from its controller");
        helper.assertTrue(grid.getPathingService().getControllerState() == ControllerState.CONTROLLER_ONLINE
            && !grid.getPathingService().isNetworkBooting() && grid.getEnergyService().isNetworkPowered(),
            "The AE2 controller did not finish booting with power");
        helper.assertTrue(head.getMaxChannels() == headCapacity && tail.getMaxChannels() == tailCapacity,
            "Wrong trunk capacity: " + head.getMaxChannels() + "/" + tail.getMaxChannels());
        helper.assertTrue(head.getUsedChannels() == expectedActive && tail.getUsedChannels() == expectedActive,
            "Wrong trunk usage: " + head.getUsedChannels() + "/" + tail.getUsedChannels() + " expected " + expectedActive);
        int drives = 0;
        int active = 0;
        for (var drive : grid.getMachineNodes(DriveBlockEntity.class)) {
            drives++;
            if (drive.isActive()) active++;
        }
        helper.assertTrue(drives == expectedDrives && active == expectedActive,
            "The grid sees " + drives + " drives with " + active + " active, expected " + expectedDrives + "/" + expectedActive);
    }
}
