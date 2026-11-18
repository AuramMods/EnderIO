package com.enderio.enderio.gametests.regressions.issues;

import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.content.conduits.type.item.ItemConduit;
import com.enderio.enderio.content.conduits.type.item.ItemConduitConnectionConfig;
import com.enderio.enderio.content.filters.item.limited.LimitedItemFilter;
import com.enderio.enderio.gametests.conduits.ConduitGameTestHelper;
import com.enderio.enderio.init.EIOConduits;
import com.enderio.enderio.init.EIODataComponents;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.gametests.util.FixtureBuilder;

// Scenario group: regression.issue1459
public class Issue1459 {

    @GameTest
    // Ensures an extract limited item filter leaves its configured stock in the source inventory.
    public static void itemConduitLimitedExtractFilter(final GameplayTest test) {
        testLimitedFilterTransfer(test, false);
    }

    @GameTest
    // Ensures an insert limited item filter caps the destination at its configured stock.
    public static void itemConduitLimitedInsertFilter(final GameplayTest test) {
        testLimitedFilterTransfer(test, true);
    }

    private static void testLimitedFilterTransfer(final GameplayTest test, boolean insertFilter) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 3)
            // Sender
            .set(0, 0, 0, Blocks.CHEST.defaultBlockState())

            // Receiver
            .set(0, 0, 2, Blocks.CHEST.defaultBlockState()));

        test.onGameTest(ConduitGameTestHelper.class, helper -> {
            var itemConduit = helper.getConduit(EIOConduits.ITEM);
            final int tickRate = itemConduit.value().type().getTickRate(itemConduit);
            final int stockLimit = 32;

            var filterStack = new ItemStack(EIOItems.LIMITED_ITEM_FILTER.get());
            var filter = new LimitedItemFilter(LimitedItemFilter.SLOT_COUNT);
            filter.matches().set(0, new ItemStack(Items.COBBLESTONE, stockLimit));
            EIODataComponents.LIMITED_ITEM_FILTER.set(filterStack, filter);

            helper.startSequence()
                // Clear and place the conduit between the source and destination chests.
                .thenExecute(() -> helper.setBlock(0, 1, 1, Blocks.AIR))
                .thenExecute(() -> helper.placeConduit(itemConduit, 0, 1, 1))
                // Extract from the source chest and insert into the destination chest.
                .thenExecute(() -> {
                    var bundle = helper.getConduitBundle(0, 1, 1, false);
                    bundle.setConnectionConfig(itemConduit, Direction.NORTH,
                        ItemConduitConnectionConfig.DEFAULT.withIsExtract(true).withIsInsert(false)
                            .withExtractRedstoneControl(RedstoneControl.ALWAYS_ACTIVE));
                    bundle.setConnectionConfig(itemConduit, Direction.SOUTH,
                        ItemConduitConnectionConfig.DEFAULT.withIsExtract(false).withIsInsert(true));
                    var filterSide = insertFilter ? Direction.SOUTH : Direction.NORTH;
                    int filterSlot = insertFilter ? ItemConduit.INSERT_FILTER_SLOT : ItemConduit.EXTRACT_FILTER_SLOT;
                    bundle.getConnectionInventory(itemConduit, filterSide).setStackInSlot(filterSlot, filterStack);
                })
                .thenExecute(() -> helper.insertIntoContainer(0, 1, 0, Items.COBBLESTONE, 64))
                // Allow enough network cycles for the permitted stock to transfer.
                .thenExecuteAfter(tickRate * 2, () -> {
                    int expectedSourceCount = insertFilter ? 64 - stockLimit : stockLimit;
                    int expectedDestinationCount = insertFilter ? stockLimit : 64 - stockLimit;
                    helper.assertContainerHasExactly(0, 1, 0, Items.COBBLESTONE, expectedSourceCount);
                    helper.assertContainerHasExactly(0, 1, 2, Items.COBBLESTONE, expectedDestinationCount);
                })
                .thenSucceed();
        });
    }
}
