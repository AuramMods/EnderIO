package com.enderio.enderio.gametests.content;

import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.Map;

public final class SteelConstructionTests {
    @GameTest
    public static void steelDoorAndTrapdoorUseVanillaPlacementPowerAndLoot(GameplayTest test) {
        var door = new BlockPos(2, 2, 2);
        var hatch = new BlockPos(6, 2, 2);
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(9, 5, 5));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var level = helper.getLevel();
            var player = FakePlayerFactory.getMinecraft(level);
            helper.setBlock(door.below(), Blocks.STONE);
            helper.setBlock(hatch.below(), Blocks.STONE);
            place(helper, player, door.below(), EIOBlocks.DARK_STEEL_DOOR.get(), Direction.UP);
            place(helper, player, hatch.below(), EIOBlocks.DARK_STEEL_TRAPDOOR.get(), Direction.UP);
            assertDoor(helper, door, false);
            assertTrapdoor(helper, hatch, false);
            for (var pos : new BlockPos[]{door, door.above(), hatch}) {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                helper.assertValueEqual(use(helper, player, pos, Direction.NORTH), InteractionResult.PASS,
                    "iron-style block stays closed on hand use at " + pos);
            }
            assertDoor(helper, door, false);
            assertTrapdoor(helper, hatch, false);

            helper.setBlock(door.above().east(), Blocks.REDSTONE_BLOCK);
            helper.setBlock(hatch.east(), Blocks.REDSTONE_BLOCK);
            assertDoor(helper, door, true);
            assertTrapdoor(helper, hatch, true);
            helper.setBlock(door.above().east(), Blocks.AIR);
            helper.setBlock(hatch.east(), Blocks.AIR);
            assertDoor(helper, door, false);
            assertTrapdoor(helper, hatch, false);

            var tool = new ItemStack(Items.DIAMOND_PICKAXE);
            helper.assertTrue(Block.getDrops(helper.getBlockState(door.above()), level, helper.absolutePos(door.above()),
                null, player, tool).isEmpty(), "upper door half has no separate loot");
            var drops = Block.getDrops(helper.getBlockState(door), level, helper.absolutePos(door), null, player, tool);
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(EIOBlocks.DARK_STEEL_DOOR.asItem())
                && drops.get(0).getCount() == 1, "lower door half gives one door");
            level.destroyBlock(helper.absolutePos(door.below()), false);
            helper.assertBlockPresent(Blocks.AIR, door);
            helper.assertBlockPresent(Blocks.AIR, door.above());
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void bothSteelBarsConnectWaterlogAndReleaseTheirNeighbors(GameplayTest test) {
        var dark = new BlockPos(2, 2, 2);
        var end = dark.east();
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(6, 5, 5));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var player = FakePlayerFactory.getMinecraft(helper.getLevel());
            helper.setBlock(dark.below(), Blocks.STONE);
            helper.setBlock(end.below(), Blocks.STONE);
            helper.setBlock(end, Blocks.WATER);
            place(helper, player, dark.below(), EIOBlocks.DARK_STEEL_BARS.get(), Direction.UP);
            place(helper, player, end.below(), EIOBlocks.END_STEEL_BARS.get(), Direction.UP);
            helper.assertTrue(helper.getBlockState(dark).getValue(IronBarsBlock.EAST), "Dark Steel Bars join the End Steel Bars");
            helper.assertTrue(helper.getBlockState(end).getValue(IronBarsBlock.WEST), "End Steel Bars join the Dark Steel Bars");
            helper.assertTrue(helper.getBlockState(end).getValue(IronBarsBlock.WATERLOGGED)
                && helper.getBlockState(end).getFluidState().is(Fluids.WATER)
                && helper.getBlockState(end).getFluidState().isSource(), "bar placement keeps the source water");
            helper.getLevel().removeBlock(helper.absolutePos(dark), false);
            helper.assertTrue(!helper.getBlockState(end).getValue(IronBarsBlock.WEST), "neighbor removal clears the adjoining arm");
            helper.assertTrue(helper.getBlockState(end).getFluidState().isSource(), "neighbor removal leaves waterlogged source intact");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void reinforcedObsidianSurvivesBlastAndRejectsWitherDestruction(GameplayTest test) {
        var center = new BlockPos(4, 3, 4);
        var protectedPos = center.east();
        var control = center.west();
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(10, 8, 10));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var level = helper.getLevel();
            helper.setBlock(protectedPos, EIOBlocks.REINFORCED_OBSIDIAN.get());
            helper.setBlock(control, Blocks.DIRT);
            var wither = EntityType.WITHER.create(level);
            helper.assertTrue(!ForgeHooks.canEntityDestroy(level, helper.absolutePos(protectedPos), wither)
                && ForgeHooks.canEntityDestroy(level, helper.absolutePos(control), wither), "Forge wither break gate protects only the reinforced block");
            var obsidian = helper.getBlockState(protectedPos);
            helper.assertTrue(obsidian.is(BlockTags.MINEABLE_WITH_PICKAXE) && obsidian.is(BlockTags.NEEDS_DIAMOND_TOOL)
                && new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(obsidian)
                && !new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(obsidian), "native harvest tags require a diamond-tier pickaxe");
            var pos = Vec3.atCenterOf(helper.absolutePos(center));
            level.explode(null, pos.x, pos.y, pos.z, 3.0F, Level.ExplosionInteraction.BLOCK);
            helper.assertBlockPresent(EIOBlocks.REINFORCED_OBSIDIAN.get(), protectedPos);
            helper.assertBlockPresent(Blocks.AIR, control);
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void steelConstructionRecipesCraftTheBaselineCounts(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(3, 4, 3));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            Map<Character, ItemLike> dark = Map.of('I', EIOItems.DARK_STEEL_INGOT.get());
            craft(helper, EIOBlocks.DARK_STEEL_LADDER.get(), 12, dark, " I ", " I ", " I ");
            craft(helper, EIOBlocks.DARK_STEEL_BARS.get(), 16, dark, "III", "III");
            craft(helper, EIOBlocks.DARK_STEEL_DOOR.get(), 3, dark, "II", "II", "II");
            craft(helper, EIOBlocks.DARK_STEEL_TRAPDOOR.get(), 1, dark, "II", "II");
            craft(helper, EIOBlocks.END_STEEL_BARS.get(), 12, Map.of('I', EIOItems.END_STEEL_INGOT.get()), "III", "III");
            craft(helper, EIOBlocks.REINFORCED_OBSIDIAN.get(), 1,
                Map.of('B', EIOBlocks.DARK_STEEL_BARS.get(), 'G', EIOItems.GRAINS_OF_INFINITY.get(), 'O', Blocks.OBSIDIAN),
                "GBG", "BOB", "GBG");
            helper.startSequence().thenSucceed();
        });
    }

    private static void place(EnderGameTestHelper helper, ServerPlayer player, BlockPos clicked, Block block, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block));
        helper.assertTrue(use(helper, player, clicked, face).consumesAction() && player.getMainHandItem().isEmpty(),
            "survival item placement consumes one " + BuiltInRegistries.BLOCK.getKey(block));
        helper.assertBlockPresent(block, clicked.relative(face));
    }

    private static InteractionResult use(EnderGameTestHelper helper, ServerPlayer player, BlockPos pos, Direction face) {
        var absolute = helper.absolutePos(pos);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 2.5);
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute).relative(face, 0.5), face, absolute, false);
        return player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    private static void assertDoor(EnderGameTestHelper helper, BlockPos pos, boolean open) {
        var lower = helper.getBlockState(pos);
        var upper = helper.getBlockState(pos.above());
        helper.assertTrue(lower.is(EIOBlocks.DARK_STEEL_DOOR.get()) && lower.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
            && upper.is(EIOBlocks.DARK_STEEL_DOOR.get()) && upper.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER,
            "registered door occupies both native halves");
        for (var state : new net.minecraft.world.level.block.state.BlockState[]{lower, upper}) {
            helper.assertTrue(state.getValue(DoorBlock.OPEN) == open && state.getValue(DoorBlock.POWERED) == open,
                "both door halves follow redstone");
        }
    }

    private static void assertTrapdoor(EnderGameTestHelper helper, BlockPos pos, boolean open) {
        var state = helper.getBlockState(pos);
        helper.assertTrue(state.is(EIOBlocks.DARK_STEEL_TRAPDOOR.get()) && state.getValue(TrapDoorBlock.OPEN) == open
            && state.getValue(TrapDoorBlock.POWERED) == open, "trapdoor follows redstone");
    }

    private static void craft(EnderGameTestHelper helper, Block block, int count, Map<Character, ItemLike> keys, String... rows) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        var grid = new TransientCraftingContainer(new CraftingMenu(0, player.getInventory()), 3, 3);
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                var item = keys.get(rows[y].charAt(x));
                if (item != null) grid.setItem(y * 3 + x, new ItemStack(item));
            }
        }
        var id = BuiltInRegistries.BLOCK.getKey(block);
        var recipe = (CraftingRecipe) level.getRecipeManager().byKey(id).orElseThrow();
        helper.assertTrue(recipe.matches(grid, level)
            && ItemStack.matches(recipe.assemble(grid, level.registryAccess()), new ItemStack(block, count)),
            id + " crafts the original count from baseline materials");
    }
}
