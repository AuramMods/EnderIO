package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.content.paint.BlockPaintData;
import com.enderio.enderio.content.paint.block.entity.DoublePaintedBlockEntity;
import com.enderio.enderio.foundation.EIONBTKeys;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.List;

public class PaintedSlabTests {
    private static final BlockPos SLAB = new BlockPos(1, 2, 1);

    @GameTest
    public static void typedSlabsPreserveBothColorsWhenTheTopIsPlacedFirst(GameplayTest test) {
        test.registerGameTestTemplate(PaintedSlabTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            place(helper, painted(Blocks.DIAMOND_BLOCK), SLAB.above(), Direction.DOWN, 0);
            assertHalf(helper, SlabType.TOP, null, Blocks.DIAMOND_BLOCK);
            place(helper, painted(Blocks.GOLD_BLOCK), SLAB, Direction.DOWN, 0.5);
            assertMixedAndLoot(helper);
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void typedSlabsPreserveBothColorsWhenTheBottomIsPlacedFirst(GameplayTest test) {
        test.registerGameTestTemplate(PaintedSlabTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            place(helper, painted(Blocks.GOLD_BLOCK), SLAB.below(), Direction.UP, 1);
            assertHalf(helper, SlabType.BOTTOM, Blocks.GOLD_BLOCK, null);
            place(helper, painted(Blocks.DIAMOND_BLOCK), SLAB, Direction.UP, 0.5);
            assertMixedAndLoot(helper);
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void legacyPortablePaintAppliesToTheHalfBeingPlaced(GameplayTest test) {
        test.registerGameTestTemplate(PaintedSlabTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            place(helper, historical(EIONBTKeys.PAINT_2, "minecraft:diamond_block"), SLAB.above(), Direction.DOWN, 0);
            assertHalf(helper, SlabType.TOP, null, Blocks.DIAMOND_BLOCK);
            place(helper, historical(EIONBTKeys.PAINT, "minecraft:gold_block"), SLAB, Direction.DOWN, 0.5);
            assertMixedAndLoot(helper);
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void missingPreviousPaintDoesNotChangeWhichHalfWasAdded(GameplayTest test) {
        test.registerGameTestTemplate(PaintedSlabTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            helper.setBlock(SLAB, EIOBlocks.PAINTED_SLAB.get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
            place(helper, painted(Blocks.GOLD_BLOCK), SLAB, Direction.DOWN, 0.5);
            assertHalf(helper, SlabType.DOUBLE, Blocks.GOLD_BLOCK, null);
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void pickingTheUndersideOfATopSlabUsesItsPaint(GameplayTest test) {
        test.registerGameTestTemplate(PaintedSlabTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            place(helper, painted(Blocks.DIAMOND_BLOCK), SLAB.above(), Direction.DOWN, 0);
            var position = helper.absolutePos(SLAB);
            var state = helper.getLevel().getBlockState(position);
            var hit = new BlockHitResult(Vec3.atLowerCornerOf(position).add(0.5, 0.5, 0.5), Direction.DOWN, position, false);
            var result = state.getBlock().getCloneItemStack(state, hit, helper.getLevel(), position, FakePlayerFactory.getMinecraft(helper.getLevel()));
            helper.assertValueEqual(BlockPaintData.ITEM_DATA.get(result), BlockPaintData.of(Blocks.DIAMOND_BLOCK),
                "pick block keeps the top paint when pointing at its underside");
            helper.startSequence().thenSucceed();
        });
    }

    private static FixtureBuilder fixture() {
        return FixtureBuilder.withSize(3, 3, 3)
            .set(1, 0, 1, Blocks.STONE.defaultBlockState())
            .set(1, 2, 1, Blocks.STONE.defaultBlockState());
    }

    private static ItemStack painted(Block paint) {
        var stack = new ItemStack(EIOBlocks.PAINTED_SLAB.get());
        BlockPaintData.ITEM_DATA.set(stack, BlockPaintData.of(paint));
        return stack;
    }

    private static ItemStack historical(String key, String paint) {
        var stack = new ItemStack(EIOBlocks.PAINTED_SLAB.get());
        var legacy = new CompoundTag();
        legacy.putString(key, paint);
        stack.addTagElement(BlockItem.BLOCK_ENTITY_TAG, legacy);
        return stack;
    }

    private static void place(EnderGameTestHelper helper, ItemStack stack, BlockPos relativeHit, Direction face, double y) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var clicked = helper.absolutePos(relativeHit);
        var hit = new BlockHitResult(Vec3.atLowerCornerOf(clicked).add(0.5, y, 0.5), face, clicked, false);
        var result = ((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
        helper.assertTrue(result.consumesAction() && stack.isEmpty(), "ordinary slab placement consumes one item");
    }

    private static void assertHalf(EnderGameTestHelper helper, SlabType type, Block lower, Block upper) {
        var entity = helper.getBlockEntity(SLAB.getX(), SLAB.getY(), SLAB.getZ(), DoublePaintedBlockEntity.class);
        helper.assertValueEqual(entity.getBlockState().getValue(SlabBlock.TYPE), type, "slab shape");
        helper.assertValueEqual(entity.getPrimaryPaint().orElse(null), lower, "lower paint");
        helper.assertValueEqual(entity.getSecondaryPaint().orElse(null), upper, "upper paint");
    }

    private static void assertMixedAndLoot(EnderGameTestHelper helper) {
        assertHalf(helper, SlabType.DOUBLE, Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK);
        var entity = helper.getBlockEntity(SLAB.getX(), SLAB.getY(), SLAB.getZ(), DoublePaintedBlockEntity.class);
        var drops = Block.getDrops(entity.getBlockState(), helper.getLevel(), entity.getBlockPos(), entity,
            FakePlayerFactory.getMinecraft(helper.getLevel()), new ItemStack(Items.DIAMOND_AXE));
        helper.assertValueEqual(drops.size(), 2, "mixed slabs yield exactly two stacks");
        helper.assertTrue(drops.stream().allMatch(drop -> drop.is(EIOBlocks.PAINTED_SLAB.get().asItem()) && drop.getCount() == 1),
            "each half produces one slab");
        helper.assertValueEqual(drops.stream().map(BlockPaintData.ITEM_DATA::get).toList(),
            List.of(BlockPaintData.of(Blocks.GOLD_BLOCK), BlockPaintData.of(Blocks.DIAMOND_BLOCK)), "each drop has its own paint");
    }
}
