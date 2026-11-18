package com.enderio.enderio.gametests.content;

import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorTier;
import com.enderio.enderio.content.misc_blocks.light.LegacyLightBlock;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;

public class LegacyLightTests {
    @GameTest
    public static void wiredFloorLightUsesOneEnergyPerTick(final GameplayTest test) {
        BlockPos bank = new BlockPos(2, 1, 2);
        BlockPos light = bank.above();
        BlockPos node = light.above();
        BlockPos switchPos = light.east();
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(5, 5, 5)
            .set(2, 0, 2, EIOBlocks.CAPACITOR_BANKS.get(CapacitorTier.BASIC).get().defaultBlockState()));

        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var level = helper.getLevel();
            int[] remainingEnergy = new int[1];
            helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.changeIoConfig(2, 1, 2, config -> config.setIOMode(Direction.UP, IOMode.PUSH));
                    helper.provideEnergyExact(2, 1, 2, 300);
                    helper.setBlock(light, EIOBlocks.POWERED_LIGHT.get().defaultBlockState()
                        .setValue(LegacyLightBlock.FACE, AttachFace.FLOOR));
                })
                .thenWaitUntil(() -> helper.assertBlockPresent(EIOBlocks.LIGHT_NODE.get(), node))
                .thenWaitUntil(() -> helper.assertValueEqual(level.getBrightness(LightLayer.BLOCK, helper.absolutePos(node)),
                    15, "spread node's block light"))
                .thenExecute(() -> {
                    helper.assertTrue(level.getBlockState(helper.absolutePos(light)).getValue(LegacyLightBlock.ENABLED),
                        "wired lamp lit through its floor support");
                    remainingEnergy[0] = helper.getEnergyStored(2, 1, 2);
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertValueEqual(helper.getEnergyStored(2, 1, 2), remainingEnergy[0] - 1, "energy after a lit tick");
                    helper.setBlock(switchPos, Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertBlockPresent(Blocks.AIR, node))
                .thenExecute(() -> remainingEnergy[0] = helper.getEnergyStored(2, 1, 2))
                .thenExecuteAfter(3, () -> {
                    helper.assertValueEqual(helper.getEnergyStored(2, 1, 2), remainingEnergy[0], "energy while switched off");
                    helper.setBlock(switchPos, Blocks.AIR);
                })
                .thenWaitUntil(() -> helper.assertBlockPresent(EIOBlocks.LIGHT_NODE.get(), node))
                .thenExecute(() -> helper.setBlock(bank, Blocks.STONE))
                .thenWaitUntil(() -> helper.assertBlockPresent(Blocks.AIR, node))
                .thenExecute(() -> helper.assertTrue(!level.getBlockState(helper.absolutePos(light)).getValue(LegacyLightBlock.ENABLED),
                    "wired lamp became dark when its energy source was removed"))
                .thenSucceed();
        });
    }
}
