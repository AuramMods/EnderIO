package com.enderio.enderio.gametests.regressions.issues;

import com.enderio.enderio.content.conduits.type.redstone.RedstoneConduitConnectionConfig;
import com.enderio.enderio.gametests.conduits.ConduitGameTestHelper;
import com.enderio.enderio.init.EIOConduits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.gametests.util.FixtureBuilder;

/**
 * Regression test for issue #1237 - Redstone conduits don't produce block update when state changes.
 * https://github.com/Team-EnderIO/EnderIO/issues/1237
 */
// Scenario group: regression.issue1237
public class Issue1237 {

    @GameTest
    public static void breakingRedstoneBridgeKeepsBothFragmentsTicking(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(5, 2, 1)
            .set(0, 0, 0, Blocks.REDSTONE_BLOCK.defaultBlockState())
            .set(4, 0, 0, Blocks.REDSTONE_LAMP.defaultBlockState()));

        test.onGameTest(ConduitGameTestHelper.class, helper -> {
            var redstone = helper.getConduit(EIOConduits.REDSTONE);
            var bridge = new BlockPos(2, 1, 0);
            var lamp = new BlockPos(4, 1, 0);
            helper.startSequence()
                .thenExecute(() -> {
                    helper.placeConduit(redstone, 1, 1, 0);
                    helper.placeConduit(redstone, 2, 1, 0);
                    helper.placeConduit(redstone, 3, 1, 0);
                    helper.getConduitBundle(3, 1, 0, false).setConnectionConfig(redstone, Direction.EAST,
                        RedstoneConduitConnectionConfig.DEFAULT.withIsExtract(false).withIsInsert(true).withInsertChannel(DyeColor.RED));
                })
                .thenExecuteAfter(8, () -> {
                    helper.assertTrue(helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "Connected lamp should light");
                    helper.setBlock(bridge, Blocks.AIR);
                    var source = helper.getConduitBundle(1, 1, 0, false).getConduitNode(redstone).getNetwork();
                    var output = helper.getConduitBundle(3, 1, 0, false).getConduitNode(redstone).getNetwork();
                    helper.assertTrue(source != output, "Bridge removal should split the network");
                    helper.assertTrue(source.nodeCount(redstone) == 1 && output.nodeCount(redstone) == 1,
                        "Each fragment must retain its one remaining redstone conduit in the tick count");
                })
                .thenExecuteAfter(8, () ->
                    helper.assertTrue(!helper.getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "Isolated lamp should turn off"))
                .thenSucceed();
        });
    }

    @GameTest
    // Tests that pistons extend when receiving redstone signal via conduit
    public static void testPistonExtensionViaRedstoneConduit(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(6, 2, 1)
            .set(0, 0, 0, Blocks.REDSTONE_BLOCK.defaultBlockState())
            .set(4, 0, 0, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST)));

        test.onGameTest(ConduitGameTestHelper.class, helper -> {
            var redstoneConduit = helper.getConduit(EIOConduits.REDSTONE);
            final int tickRate = redstoneConduit.value().type().getTickRate(redstoneConduit);

            helper.startSequence()
                // Place conduits to form a network
                .thenExecute(() -> {
                    // connects to redstone block
                    helper.placeConduit(redstoneConduit, 1, 1, 0);
                    helper.placeConduit(redstoneConduit, 2, 1, 0);
                    // connects to piston
                    helper.placeConduit(redstoneConduit, 3, 1, 0);
                })
                
                // Configure RED channel input (extract from redstone block)
                .thenExecute(() -> helper
                    .getConduitBundle(1, 1, 0, false)
                    .setConnectionConfig(redstoneConduit, Direction.WEST,
                        RedstoneConduitConnectionConfig.DEFAULT
                            .withIsExtract(true)
                            .withIsInsert(false)
                            .withExtractChannel(DyeColor.RED)))
                
                // Configure RED channel output (insert to piston)
                .thenExecute(() -> helper
                    .getConduitBundle(3, 1, 0, false)
                    .setConnectionConfig(redstoneConduit, Direction.EAST,
                        RedstoneConduitConnectionConfig.DEFAULT
                            .withIsInsert(true)
                            .withIsExtract(false)
                            .withInsertChannel(DyeColor.RED)))
                
                // Wait for network to tick before checking
                .thenExecuteAfter(tickRate * 2, () -> {
                    var pistonState = helper.getBlockState(new BlockPos(4, 1, 0));
                    boolean isExtended = pistonState.getValue(PistonBaseBlock.EXTENDED);
                    
                    if (!isExtended) {
                        throw new GameTestAssertException(
                            "Piston should extend when redstone conduit transmits signal");
                    }
                })
                .thenSucceed();
        });
    }
}
