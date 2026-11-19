package com.enderio.enderio.gametests.conduits.item;

import com.enderio.enderio.gametests.EnderIOTests;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.gametests.conduits.ConduitGameTestHelper;
import com.enderio.enderio.content.conduits.menu.ConduitMenu;
import com.enderio.enderio.content.conduits.type.item.ItemConduit;
import com.enderio.enderio.content.conduits.type.item.ItemConduitConnectionConfig;
import com.enderio.enderio.init.EIOConduits;
import com.enderio.enderio.init.EIOItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.gametests.util.FixtureTemplate;
import com.enderio.enderio.gametests.util.FixtureBuilder;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

// Scenario group: conduit.item
public class ItemConduitTests {

    private static final String THREE_CHESTS = EnderIOTests.MOD_ID + ":item_conduit_three_chests";

    // @formatter:off
    @FixtureTemplate(THREE_CHESTS)
    public static final Supplier<StructureTemplate> THREE_CHESTS_TEMPLATE = FixtureBuilder.lazy(3, 1, 3,
        builder -> builder
            // Sender
            .set(2, 0, 0, Blocks.CHEST.defaultBlockState())

            // Receivers
            .set(0, 0, 2, Blocks.CHEST.defaultBlockState())
            .set(2, 0, 2, Blocks.CHEST.defaultBlockState())
    );
    // @formatter:on

    @GameTest
    // Tests basic item conduit functionality.
    public static void itemConduitBasicTransfer(final GameplayTest test) {
        test.registerGameTestTemplate(
            () -> FixtureBuilder.withSize(1, 1, 3).set(0, 0, 0, Blocks.CHEST.defaultBlockState()).set(0, 0, 2, Blocks.CHEST.defaultBlockState()));

        test.onGameTest(ConduitGameTestHelper.class, helper -> {
            var itemConduit = helper.getConduit(EIOConduits.ITEM);
            final int tickRate = itemConduit.value().type().getTickRate(itemConduit);

            helper.startSequence()
                // Destroy any previous conduit
                .thenExecute(() -> helper.setBlock(0, 1, 1, Blocks.AIR))
                // Place a conduit between the two chests
                .thenExecute(() -> helper.placeConduit(itemConduit, 0, 1, 1))
                // Configure the extract end
                .thenExecute(() -> helper
                    .getConduitBundle(0, 1, 1, false)
                    .setConnectionConfig(itemConduit, Direction.NORTH,
                        ItemConduitConnectionConfig.DEFAULT.withIsExtract(true).withIsInsert(false).withExtractRedstoneControl(RedstoneControl.NEVER_ACTIVE)))
                // Configure the insert end
                .thenExecute(() -> helper
                    .getConduitBundle(0, 1, 1, false)
                    .setConnectionConfig(itemConduit, Direction.SOUTH, ItemConduitConnectionConfig.DEFAULT.withIsExtract(false).withIsInsert(true)))
                // Put some dirt in the chest we'll extract from
                .thenExecute(() -> helper.insertIntoContainer(0, 1, 0, Items.DIRT, 1))
                // Ensure the item is still there
                .thenExecuteAfter(tickRate, () -> helper.assertContainerHasExactly(0, 1, 0, Items.DIRT, 1))
                // Now enable movement with redstone control
                .thenExecute(() -> helper
                    .getConduitBundle(0, 1, 1, false)
                    .setConnectionConfig(itemConduit, Direction.NORTH,
                        ItemConduitConnectionConfig.DEFAULT.withIsExtract(true).withIsInsert(false).withExtractRedstoneControl(RedstoneControl.ALWAYS_ACTIVE)))
                // Ensure the item moves
                .thenExecuteAfter(tickRate, () -> helper.assertContainerHasExactly(0, 1, 2, Items.DIRT, 1))
                // Ensure no duplication
                .thenExecuteAfter(tickRate, () -> helper.assertContainerHasExactly(0, 1, 0, Items.DIRT, 0))
                .thenSucceed();
        });
    }

    @GameTest
    public static void legacyUpgradeCanBeInsertedAndRemovedInConduitMenu(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 3)
            .set(0, 0, 0, Blocks.BARREL.defaultBlockState()));
        test.onGameTest(ConduitGameTestHelper.class, helper -> {
            var conduit = helper.getConduit(EIOConduits.ITEM);
            helper.startSequence().thenExecute(() -> {
                helper.placeConduit(conduit, 0, 1, 1);
                var bundle = helper.getConduitBundle(0, 1, 1, false);
                var player = FakePlayerFactory.getMinecraft(helper.getLevel());
                var menu = new ConduitMenu(1, player.getInventory(), bundle, conduit, Direction.NORTH);
                var upgrade = EIOItems.LEGACY_EXTRACTION_UPGRADES.get(3).get();
                var stack = new ItemStack(upgrade, 3);
                stack.getOrCreateTag().putInt("PortMenuUpgrade", 4);

                int slot = ItemConduit.EXTRACTION_UPGRADE_SLOT;
                helper.assertValueEqual(menu.getSlot(slot).mayPlace(new ItemStack(Items.DIAMOND)), false,
                    "Upgrade slot rejects unrelated items");
                helper.assertValueEqual(menu.getSlot(ItemConduit.EXTRACT_FILTER_SLOT).mayPlace(stack), false,
                    "Upgrade is distinct from the extraction filter");
                menu.setCarried(stack);
                menu.clicked(slot, 0, ClickType.PICKUP, player);
                var installed = bundle.getConnectionInventory(conduit, Direction.NORTH).getStackInSlot(slot);
                helper.assertValueEqual(installed.is(upgrade) && installed.getCount() == 1, true,
                    "Menu places exactly one upgrade");
                helper.assertValueEqual(menu.getCarried().getCount(), 2, "Remaining upgrades stay on the cursor");
                menu.setCarried(ItemStack.EMPTY);
                menu.clicked(slot, 0, ClickType.PICKUP, player);
                helper.assertValueEqual(menu.getCarried().is(upgrade) && menu.getCarried().getCount() == 1
                    && menu.getCarried().getTag().getInt("PortMenuUpgrade") == 4, true,
                    "Picking up the installed upgrade preserves its tag");
                helper.assertValueEqual(menu.getSlot(slot).hasItem(), false, "Menu removal clears the upgrade slot");
            }).thenSucceed();
        });
    }

    @GameTest
    public static void conduitMenuRequiresThePlayersDimension(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 2)
            .set(0, 0, 0, Blocks.BARREL.defaultBlockState()));
        test.onGameTest(ConduitGameTestHelper.class, helper -> helper.startSequence().thenExecute(() -> {
            var conduit = helper.getConduit(EIOConduits.ITEM);
            helper.placeConduit(conduit, 0, 1, 1);
            var bundle = helper.getConduitBundle(0, 1, 1, false);
            var position = Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 1, 1))).add(0, 0, 1);
            var local = helper.getPlayer();
            local.setPos(position.x, position.y, position.z);
            var menu = new ConduitMenu(1, local.getInventory(), bundle, conduit, Direction.NORTH);
            helper.assertValueEqual(menu.stillValid(local), true, "The live conduit menu accepts a nearby player");

            var level = helper.getLevel();
            var otherDimension = level.dimension() == Level.OVERWORLD ? Level.NETHER : Level.OVERWORLD;
            var otherLevel = Objects.requireNonNull(level.getServer().getLevel(otherDimension));
            var remote = FakePlayerFactory.get(otherLevel, new GameProfile(UUID.randomUUID(), "ConduitMenuTest"));
            remote.setPos(position.x, position.y, position.z);
            helper.assertValueEqual(menu.stillValid(remote), false,
                "The same coordinates in another dimension cannot keep the conduit menu open");
        }).thenSucceed());
    }

    @GameTest(template = THREE_CHESTS)
    // Ensures item conduits prioritise closest container first.
    public static void itemConduitDistancePriority(final ConduitGameTestHelper helper) {
        var itemConduit = helper.getConduit(EIOConduits.ITEM);
        final int tickRate = itemConduit.value().type().getTickRate(itemConduit);

        helper.startSequence()
            // Destroy all previous conduits
            .thenExecute(() -> helper.fillAir(1, 1, 0, 1, 1, 2))
            // Place a conduit between the two chests
            .thenExecute(() -> helper.fillConduits(itemConduit, 1, 1, 0, 1, 1, 2))
            // Configure the insert ends
            .thenExecute(() -> helper
                .getConduitBundle(1, 1, 2, false)
                .setConnectionConfig(itemConduit, Direction.EAST, ItemConduitConnectionConfig.DEFAULT.withIsExtract(false).withIsInsert(true)))
            .thenExecute(() -> helper
                .getConduitBundle(1, 1, 2, false)
                .setConnectionConfig(itemConduit, Direction.WEST, ItemConduitConnectionConfig.DEFAULT.withIsExtract(false).withIsInsert(true)))
            // Configure the extract end
            .thenExecute(() -> helper
                .getConduitBundle(1, 1, 0, false)
                .setConnectionConfig(itemConduit, Direction.EAST,
                    ItemConduitConnectionConfig.DEFAULT.withIsExtract(true).withIsInsert(false).withExtractRedstoneControl(RedstoneControl.ALWAYS_ACTIVE)))
            // Put some dirt in the chest we'll extract from
            .thenExecute(() -> helper.insertIntoContainer(2, 1, 0, Items.DIRT, 1))
            // Ensure the item moves to the closer chest
            .thenExecuteAfter(tickRate, () -> helper.assertContainerHasExactly(2, 1, 2, Items.DIRT, 1))
            .thenExecuteAfter(tickRate, () -> helper.assertContainerHasExactly(0, 1, 2, Items.DIRT, 0))
            .thenSucceed();
    }

    @GameTest(template = THREE_CHESTS)
    // Ensures item conduits prioritise highest priority container first, before closest.
    public static void itemConduitManualPriority(final ConduitGameTestHelper helper) {
        var itemConduit = helper.getConduit(EIOConduits.ITEM);
        final int tickRate = itemConduit.value().type().getTickRate(itemConduit);

        helper.startSequence()
            // Destroy all previous conduits
            .thenExecute(() -> helper.fillAir(1, 1, 0, 1, 1, 2))
            // Place a conduit between the two chests
            .thenExecute(() -> helper.fillConduits(itemConduit, 1, 1, 0, 1, 1, 2))
            // Configure the insert ends
            .thenExecute(() -> helper
                .getConduitBundle(1, 1, 2, false)
                .setConnectionConfig(itemConduit, Direction.EAST, ItemConduitConnectionConfig.DEFAULT.withIsExtract(false).withIsInsert(true)))
            .thenExecute(() -> helper
                .getConduitBundle(1, 1, 2, false)
                .setConnectionConfig(itemConduit, Direction.WEST, ItemConduitConnectionConfig.DEFAULT.withIsExtract(false).withIsInsert(true).withPriority(2)))
            // Configure the extract end
            .thenExecute(() -> helper
                .getConduitBundle(1, 1, 0, false)
                .setConnectionConfig(itemConduit, Direction.EAST,
                    ItemConduitConnectionConfig.DEFAULT.withIsExtract(true).withIsInsert(false).withExtractRedstoneControl(RedstoneControl.ALWAYS_ACTIVE)))
            // Put some dirt in the chest we'll extract from
            .thenExecute(() -> helper.insertIntoContainer(2, 1, 0, Items.DIRT, 1))
            // Ensure the item moves to the closer chest
            .thenExecuteAfter(tickRate, () -> helper.assertContainerHasExactly(0, 1, 2, Items.DIRT, 1))
            // Place an item into the first chest
            .thenSucceed();
    }

    @GameTest(template = THREE_CHESTS)
    // Ensures item conduit round robin works.
    public static void itemConduitRoundRobin(final ConduitGameTestHelper helper) {
        var itemConduit = helper.getConduit(EIOConduits.ITEM);
        final int tickRate = itemConduit.value().type().getTickRate(itemConduit);
        final int transferRate = ((ItemConduit) itemConduit.value()).transferRatePerCycle();

        helper.startSequence()
            // Destroy all previous conduits
            .thenExecute(() -> helper.fillAir(1, 1, 0, 1, 1, 2))
            // Place a conduit between the two chests
            .thenExecute(() -> helper.fillConduits(itemConduit, 1, 1, 0, 1, 1, 2))
            // Configure the insert ends
            .thenExecute(() -> helper
                .getConduitBundle(1, 1, 2, false)
                .setConnectionConfig(itemConduit, Direction.EAST, ItemConduitConnectionConfig.DEFAULT.withIsExtract(false).withIsInsert(true)))
            .thenExecute(() -> helper
                .getConduitBundle(1, 1, 2, false)
                .setConnectionConfig(itemConduit, Direction.WEST, ItemConduitConnectionConfig.DEFAULT.withIsExtract(false).withIsInsert(true)))
            // Configure the extract end
            .thenExecute(() -> helper
                .getConduitBundle(1, 1, 0, false)
                .setConnectionConfig(itemConduit, Direction.EAST, ItemConduitConnectionConfig.DEFAULT
                    .withIsExtract(true)
                    .withIsInsert(false)
                    .withExtractRedstoneControl(RedstoneControl.ALWAYS_ACTIVE)
                    .withIsRoundRobin(true)))
            // Put some dirt in the chest we'll extract from, 2x transfer rate of our
            // conduit so we can follow two network ticks.
            .thenExecute(() -> helper.insertIntoContainer(2, 1, 0, Items.DIRT, transferRate * 2))
            // Ensure the max transfer amount reaches the closest chest first, then the
            // second chest
            .thenExecuteAfter(tickRate, () -> {
                helper.assertContainerHasExactly(2, 1, 2, Items.DIRT, transferRate);
                helper.assertContainerEmpty(0, 1, 2);
            })
            .thenExecuteAfter(tickRate, () -> {
                helper.assertContainerHasExactly(2, 1, 2, Items.DIRT, transferRate);
                helper.assertContainerHasExactly(0, 1, 2, Items.DIRT, transferRate);
            })
            // Place an item into the first chest
            .thenSucceed();
    }

    // TODO: Test item filters?
}
