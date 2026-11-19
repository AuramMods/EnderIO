package com.enderio.enderio.gametests.conduits.item;

import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.content.conduits.type.item.ItemConduitConnectionConfig;
import com.enderio.enderio.content.conduits.type.item.ItemConduitTicker;
import com.enderio.enderio.gametests.conduits.ConduitGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOConduits;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

public class ItemConduitRateTests {
    private static final int TOTAL = 256;

    @GameTest
    public static void stockItemRouteMoves32Every20Ticks(GameplayTest test) {
        rate(test, EIOConduits.ITEM, EIOConduits.ITEM, 32, 0);
    }

    @GameTest
    public static void energeticItemRouteMoves64Every20Ticks(GameplayTest test) {
        rate(test, EIOConduits.ENERGETIC_ITEM, EIOConduits.ENERGETIC_ITEM, 64, 0);
    }

    @GameTest
    public static void vibrantItemRouteMoves64Every10Ticks(GameplayTest test) {
        rate(test, EIOConduits.VIBRANT_ITEM, EIOConduits.VIBRANT_ITEM, 64, 64);
    }

    @GameTest
    public static void mixedRouteUsesEnergeticExtractionPacing(GameplayTest test) {
        rate(test, EIOConduits.ENERGETIC_ITEM, EIOConduits.VIBRANT_ITEM, 64, 0);
    }

    @GameTest
    public static void mixedRouteCapsVibrantExtractionAtTheEnergeticRate(GameplayTest test) {
        rate(test, EIOConduits.VIBRANT_ITEM, EIOConduits.ENERGETIC_ITEM, 32, 32);
    }

    private static void rate(GameplayTest test, ResourceKey<Conduit<?, ?>> sourceType,
            ResourceKey<Conduit<?, ?>> targetType, int first, int halfway) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(4, 1, 1)
            .set(0, 0, 0, Blocks.BARREL.defaultBlockState())
            .set(3, 0, 0, Blocks.BARREL.defaultBlockState()));
        test.onGameTest(ConduitGameTestHelper.class, helper -> {
            var source = helper.getConduit(sourceType);
            var target = helper.getConduit(targetType);
            var pull = ItemConduitConnectionConfig.DEFAULT.withIsExtract(true).withIsInsert(false)
                .withExtractRedstoneControl(RedstoneControl.ALWAYS_ACTIVE);
            var push = ItemConduitConnectionConfig.DEFAULT.withIsExtract(false).withIsInsert(true);
            helper.startSequence().thenExecute(() -> {
                helper.placeConduit(source, 1, 1, 0);
                helper.placeConduit(target, 2, 1, 0);
                helper.getConduitBundle(1, 1, 0, false).setConnectionConfig(source, Direction.WEST, pull);
                helper.getConduitBundle(2, 1, 0, false).setConnectionConfig(target, Direction.EAST, push);
            }).thenExecuteAfter(3, () -> {
                var network = helper.getConduitBundle(1, 1, 0, false).getConduitNode(source).getNetwork();
                network.ensureCachesReady();
                var paper = new ItemStack(Items.PAPER, TOTAL);
                paper.getOrCreateTag().putString("ItemTierRate", "preserved");
                helper.insertIntoContainer(0, 1, 0, paper);
                int origin = (int) Math.floorMod(-helper.getLevel().getGameTime(), 20);
                // Sample real scheduler phases in one callback so the world cannot advance between samples.
                int expected = 0;
                for (int tick = 0; tick <= 20; tick++) {
                    ItemConduitTicker.INSTANCE.tick(helper.getLevel(), network, origin + tick);
                    if (tick == 0 || tick == 20) expected += first;
                    if (tick == 10) expected += halfway;
                    balance(helper, expected);
                }
            }).thenSucceed();
        });
    }

    private static void balance(ConduitGameTestHelper helper, int delivered) {
        helper.assertContainerHasExactly(0, 1, 0, Items.PAPER, TOTAL - delivered);
        helper.assertContainerHasExactly(3, 1, 0, Items.PAPER, delivered);
        for (int x : new int[]{0, 3}) {
            var barrel = helper.getBlockEntity(x, 1, 0, BarrelBlockEntity.class);
            for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
                var stack = barrel.getItem(slot);
                helper.assertTrue(stack.isEmpty() || stack.hasTag() && stack.getTag().getString("ItemTierRate").equals("preserved"),
                    "item transfers preserve NBT in both endpoints");
            }
        }
    }
}
