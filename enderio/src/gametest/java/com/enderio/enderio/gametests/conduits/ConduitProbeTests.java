package com.enderio.enderio.gametests.conduits;

import com.enderio.enderio.content.conduits.type.energy.EnergyConduitConnectionConfig;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOConduits;
import com.enderio.enderio.init.EIOItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ConduitProbeTests {
    @GameTest
    public static void probeCopiesAndPastesWithTranslatableConduitNames(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(4, 2, 1));
        test.onGameTest(ConduitGameTestHelper.class, helper -> helper.startSequence().thenExecute(() -> {
            var energy = helper.getConduit(EIOConduits.ENERGY);
            helper.setBlock(0, 1, 0, EIOBlocks.WIRED_CHARGER.get());
            helper.setBlock(3, 1, 0, EIOBlocks.WIRED_CHARGER.get());
            helper.placeConduit(energy, 1, 1, 0);
            helper.placeConduit(energy, 2, 1, 0);
            var source = helper.getConduitBundle(1, 1, 0, false);
            var target = helper.getConduitBundle(2, 1, 0, false);
            var original = EnergyConduitConnectionConfig.DEFAULT.withPriority(7).withIsExtract(false);
            source.setConnectionConfig(energy, Direction.WEST, original);
            source.updateShape();

            var probe = EIOItems.CONDUIT_PROBE.get();
            var stack = new ItemStack(probe);
            var player = new ProbePlayer(helper.getLevel());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            probe.switchState(player, stack);
            player.setShiftKeyDown(true);
            var sourcePos = source.getBlockPos();
            var sourceHit = new BlockHitResult(Vec3.atCenterOf(sourcePos).add(-0.49, 0, 0), Direction.WEST, sourcePos, false);
            helper.assertValueEqual(probe.onItemUseFirst(stack, new UseOnContext(player, InteractionHand.MAIN_HAND, sourceHit)),
                InteractionResult.SUCCESS, "Copy through the west connector");
            var tooltip = new ArrayList<Component>();
            probe.appendHoverText(stack, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
            helper.assertTrue(tooltip.stream().anyMatch(ConduitProbeTests::containsEnergyName),
                "The probe tooltip must use a translatable name from the world's conduit definition");

            player.setShiftKeyDown(false);
            var targetPos = target.getBlockPos();
            var targetHit = new BlockHitResult(Vec3.atCenterOf(targetPos).add(0.49, 0, 0), Direction.EAST, targetPos, false);
            helper.assertValueEqual(probe.onItemUseFirst(stack, new UseOnContext(player, InteractionHand.MAIN_HAND, targetHit)),
                InteractionResult.SUCCESS, "Paste through the east connector");
            helper.assertValueEqual(target.getConnectionConfig(energy, Direction.EAST), original,
                "Paste must preserve the exact configured state");
            helper.assertTrue(player.messages.size() == 2 && player.messages.stream().allMatch(ConduitProbeTests::containsEnergyName),
                "Both confirmations must keep the conduit name translatable when sent to the client");
        }).thenSucceed());
    }

    private static boolean containsEnergyName(Component component) {
        return Component.Serializer.toJson(component).contains("\"translate\":\"conduit.enderio.energy\"");
    }

    private static final class ProbePlayer extends FakePlayer {
        private final List<Component> messages = new ArrayList<>();

        private ProbePlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "[EnderIO] probe test"));
        }

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message);
        }
    }
}
