package com.enderio.enderio.gametests.conduits;

import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.content.conduits.type.redstone.RedstoneConduitNetworkContext;
import com.enderio.enderio.content.filters.redstone.RedstoneTimerFilter;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

public class RedstoneFilterStateTests {
    @GameTest
    public static void savedTimerResumesItsPartialPeriod(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1));
        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence().thenExecute(() -> {
            var stack = new ItemStack(EIOItems.TIMER_FILTER.get());
            var timer = timer(stack);
            timer.setMaxTicks(8);
            helper.assertTrue(readTimer(helper, timer) == 0, "Timer pulsed after only two ticks");
            helper.assertTrue(readTimer(helper, timer) == 0, "Timer pulsed after only four ticks");

            var restored = timer(reopen(stack));
            helper.assertTrue(restored.getTicks() == 4 && restored.getMaxTicks() == 8,
                "Serialized timer lost its phase or period");
            helper.assertTrue(readTimer(helper, restored) == 0, "Reopened timer pulsed at six ticks");
            helper.assertTrue(readTimer(helper, restored) == 15, "Reopened timer missed its eight-tick pulse");
            helper.assertTrue(readTimer(helper, restored) == 0, "Timer did not clear after the pulse");
        }).thenSucceed());
    }

    @GameTest
    public static void savedLatchKeepsOutputAndWaitsForNextEdge(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1));
        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence().thenExecute(() -> {
            var stack = new ItemStack(EIOItems.TLATCH_FILTER.get());
            var latch = stack.getCapability(EnderIOCapabilities.REDSTONE_INSERT_FILTER).orElseThrow(IllegalStateException::new);
            var off = new RedstoneConduitNetworkContext();
            var on = new RedstoneConduitNetworkContext();
            on.setSignal(DyeColor.WHITE, 15);
            helper.assertTrue(latch.getOutputSignal(off, DyeColor.WHITE) == 0, "Fresh latch already had output");
            latch.updateOutput(on, DyeColor.WHITE);
            helper.assertTrue(latch.getOutputSignal(on, DyeColor.WHITE) == 15,
                "Latch missed its rising edge");

            var restored = reopen(stack).getCapability(EnderIOCapabilities.REDSTONE_INSERT_FILTER)
                .orElseThrow(IllegalStateException::new);
            helper.assertTrue(restored.getOutputSignal(off, DyeColor.WHITE) == 15,
                "An early neighbor read changed the latch before its input was sampled");
            restored.updateOutput(on, DyeColor.WHITE);
            helper.assertTrue(restored.getOutputSignal(on, DyeColor.WHITE) == 15,
                "Saved latch changed while its input was held high");
            restored.updateOutput(off, DyeColor.WHITE);
            helper.assertTrue(restored.getOutputSignal(off, DyeColor.WHITE) == 15,
                "Latch changed on a falling edge");
            restored.updateOutput(on, DyeColor.WHITE);
            helper.assertTrue(restored.getOutputSignal(on, DyeColor.WHITE) == 0,
                "Reopened latch missed the next rising edge");
        }).thenSucceed());
    }

    private static ItemStack reopen(ItemStack stack) {
        return ItemStack.of(stack.save(new CompoundTag()));
    }

    private static RedstoneTimerFilter timer(ItemStack stack) {
        return (RedstoneTimerFilter) stack.getCapability(EnderIOCapabilities.REDSTONE_EXTRACT_FILTER)
            .orElseThrow(IllegalStateException::new);
    }

    private static int readTimer(EnderGameTestHelper helper, RedstoneTimerFilter timer) {
        return timer.getInputSignal(helper.getLevel(), BlockPos.ZERO, Direction.WEST);
    }
}
