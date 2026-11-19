package com.enderio.enderio.gametests.machines;

import com.enderio.core.common.serialization.StackDataCodecs;
import com.enderio.enderio.content.machines.vat.VatBlockEntity;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class VatOutputTests {
    @GameTest
    public static void nativeDropAndPlacementKeepPaidFluidRemainder(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(3, 2, 1)
            .set(0, 0, 0, Blocks.STONE.defaultBlockState())
            .set(0, 1, 0, EIOBlocks.VAT.get().defaultBlockState())
            .set(2, 0, 0, Blocks.STONE.defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence().thenExecute(() -> {
            var level = helper.getLevel();
            var original = helper.getBlockEntity(0, 2, 0, VatBlockEntity.class);
            BlockPos oldPos = original.getBlockPos();
            var paid = new FluidStack(EIOFluids.CLOUD_SEED.source().get(), 12_302);
            paid.getOrCreateTag().putIntArray("PortTestBatch", new int[] {14, 20, 1});
            var saved = original.saveWithoutMetadata();
            saved.put("PendingOutput", StackDataCodecs.saveFluid(paid));
            original.load(saved);

            var drops = Block.getDrops(original.getBlockState(), level, oldPos, original);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(EIOBlocks.VAT.asItem()), "Native VAT loot lost the machine");
            var stack = ItemStack.of(drops.get(0).save(new CompoundTag()));
            var contents = EIODataComponents.NAMED_FLUID_CONTENTS.get(stack);
            helper.assertTrue(contents != null && contents.copy("pending_output").isFluidStackIdentical(paid),
                "Native VAT loot lost the paid amount or typed fluid NBT");

            level.removeBlock(oldPos, false);
            BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 0));
            var hit = new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false);
            var context = new UseOnContext(level, null, InteractionHand.MAIN_HAND, stack, hit);
            var result = ((BlockItem) stack.getItem()).place(new BlockPlaceContext(context));
            helper.assertTrue(result.consumesAction() && stack.isEmpty(), "Native BlockItem placement did not consume the carried VAT");
        }).thenExecuteAfter(3, () -> {
            var restored = helper.getBlockEntity(2, 2, 0, VatBlockEntity.class);
            assertPublished(helper, restored, 8000);
            var drained = restored.getOutputTank().drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(drained.getAmount() == 8000, "The first published portion could not be collected");
        }).thenExecuteAfter(3, () -> {
            var restored = helper.getBlockEntity(2, 2, 0, VatBlockEntity.class);
            assertPublished(helper, restored, 4302);
            helper.assertTrue(!restored.saveWithoutMetadata().contains("PendingOutput"), "Final publication left a copy of the paid queue");
        }).thenSucceed());
    }

    private static void assertPublished(EnderGameTestHelper helper, VatBlockEntity restored, int amount) {
        var output = restored.getOutputTank().getFluid();
        helper.assertTrue(output.getFluid() == EIOFluids.CLOUD_SEED.source().get()
            && output.getAmount() == amount && output.getTag() != null
            && java.util.Arrays.equals(output.getTag().getIntArray("PortTestBatch"), new int[] {14, 20, 1}),
            "Placed VAT published the wrong tagged portion");
    }
}
