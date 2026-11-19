package com.enderio.enderio.gametests.content;

import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.UUID;

public final class PlayerReachTests {
    @GameTest
    public static void blockReachPreservesTheForgeAttributeAndCreativePadding(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(8, 3, 3));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var target = helper.absolutePos(new BlockPos(5, 2, 1));
            var center = Vec3.atCenterOf(target);
            var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "BlockReachTest"));
            var reach = player.getAttribute(ForgeMod.BLOCK_REACH.get());
            reach.setBaseValue(4.5);
            player.setGameMode(GameType.SURVIVAL);
            player.moveTo(center.x - 4.75, center.y - player.getEyeHeight(), center.z, 0, 0);
            helper.assertTrue(!player.canReach(target, 0) && !player.canReachRaw(target, 0),
                "Survival rejects the target beyond unpadded block reach");
            helper.assertTrue(player.canReach(target, 0.5) && player.canReachRaw(target, 0.5),
                "Both checks use the supplied padding");

            player.setGameMode(GameType.CREATIVE);
            helper.assertTrue(player.canReach(target, 0) && !player.canReachRaw(target, 0),
                "Creative adds its block reach bonus only to the adjusted check");
            reach.setBaseValue(0);
            helper.assertTrue(!player.canReach(target, 0.5) && !player.canReachRaw(target, 0.5),
                "A zero block reach attribute suppresses the creative bonus");
            reach.setBaseValue(6);
            helper.assertTrue(player.canReach(target, 0) && player.canReachRaw(target, 0),
                "Both checks follow live block reach attribute changes");
            helper.startSequence().thenSucceed();
        });
    }
}
