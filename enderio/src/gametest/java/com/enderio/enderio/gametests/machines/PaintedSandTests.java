package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.content.paint.BlockPaintData;
import com.enderio.enderio.content.paint.PaintedSandEntity;
import com.enderio.enderio.content.paint.block.entity.SinglePaintedBlockEntity;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;

public class PaintedSandTests {
    private static final BlockPos SOURCE = new BlockPos(1, 7, 1);
    private static final BlockPos LANDING = new BlockPos(1, 2, 1);
    private static final BlockPaintData DIAMOND = BlockPaintData.of(Blocks.DIAMOND_BLOCK);

    @GameTest
    public static void paintedSandLandsAndHarvestsWithItsOriginalPaint(GameplayTest test) {
        test.registerGameTestTemplate(PaintedSandTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            placeAndRelease(helper);
            helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> assertFallingPaint(helper))
                .thenIdle(25)
                .thenExecute(() -> {
                    var sand = helper.getBlockEntity(1, 2, 1, SinglePaintedBlockEntity.class);
                    helper.assertValueEqual(sand.getPrimaryPaint().orElse(null), Blocks.DIAMOND_BLOCK,
                        "landing restores the original paint");
                    var drops = Block.getDrops(sand.getBlockState(), helper.getLevel(), sand.getBlockPos(), sand,
                        FakePlayerFactory.getMinecraft(helper.getLevel()), new ItemStack(Items.DIAMOND_SHOVEL));
                    helper.assertTrue(drops.size() == 1 && isPaintedSand(drops.get(0)),
                        "harvesting the landed block yields one sand item with the original paint");
                })
                .thenSucceed();
        });
    }

    @GameTest
    public static void paintedSandDropsAnItemWithItsPaintWhenLandingIsBlocked(GameplayTest test) {
        test.registerGameTestTemplate(PaintedSandTests::fixture);
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            helper.setBlock(LANDING, Blocks.TORCH);
            placeAndRelease(helper);
            helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.TORCH, LANDING);
                    var bounds = new AABB(helper.absolutePos(LANDING)).inflate(1);
                    var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds);
                    helper.assertTrue(drops.size() == 1 && isPaintedSand(drops.get(0).getItem()),
                        "the blocked fall yields one sand item with the original paint");
                })
                .thenSucceed();
        });
    }

    private static FixtureBuilder fixture() {
        return FixtureBuilder.withSize(3, 8, 3).set(1, 0, 1, Blocks.STONE.defaultBlockState())
            .set(1, 5, 1, Blocks.STONE.defaultBlockState());
    }

    private static void placeAndRelease(EnderGameTestHelper helper) {
        var stack = new ItemStack(EIOBlocks.PAINTED_SAND.get());
        BlockPaintData.ITEM_DATA.set(stack, DIAMOND);
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var target = helper.absolutePos(SOURCE);
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(target), Direction.UP, target.below(), false);
        var result = ((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
        helper.assertTrue(result.consumesAction() && stack.isEmpty(), "native placement consumes one painted sand item");
        helper.assertBlockPresent(EIOBlocks.PAINTED_SAND.get(), SOURCE);
        helper.setBlock(SOURCE.below(), Blocks.AIR);
    }

    private static void assertFallingPaint(EnderGameTestHelper helper) {
        var bounds = new AABB(helper.absolutePos(SOURCE)).expandTowards(0, -4, 0);
        var falling = helper.getLevel().getEntitiesOfClass(PaintedSandEntity.class, bounds);
        helper.assertBlockPresent(Blocks.AIR, SOURCE);
        helper.assertTrue(falling.size() == 1 && falling.get(0).getBlockState().is(EIOBlocks.PAINTED_SAND.get())
            && falling.get(0).getPaint() == Blocks.DIAMOND_BLOCK, "the spawned native entity carries the original sand state and paint");
    }

    private static boolean isPaintedSand(ItemStack stack) {
        return stack.is(EIOBlocks.PAINTED_SAND.get().asItem()) && stack.getCount() == 1
            && DIAMOND.equals(BlockPaintData.ITEM_DATA.get(stack));
    }
}
