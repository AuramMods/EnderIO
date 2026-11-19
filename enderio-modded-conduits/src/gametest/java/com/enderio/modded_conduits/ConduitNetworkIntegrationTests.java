package com.enderio.modded_conduits;

import appeng.api.networking.GridHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import com.enderio.enderio.api.EnderIORegistries;
import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.content.conduits.ConduitBlockItem;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.modded_conduits.common.modules.refinedstorage.RefinedStorageCommonModule;
import com.refinedmods.refinedstorage.RSBlocks;
import com.refinedmods.refinedstorage.api.network.node.INetworkNode;
import com.refinedmods.refinedstorage.blockentity.ControllerBlockEntity;
import com.refinedmods.refinedstorage.capability.NetworkNodeProxyCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder("enderio_modded_conduits_tests")
@PrefixGameTestTemplate(false)
public class ConduitNetworkIntegrationTests {
    private static Holder<Conduit<?, ?>> rs(GameTestHelper helper) {
        return helper.getLevel().registryAccess().registryOrThrow(EnderIORegistries.Keys.CONDUIT)
            .getHolderOrThrow(RefinedStorageCommonModule.ConduitKeys.RS);
    }

    public static ConduitBundleBlockEntity place(GameTestHelper helper, Holder<Conduit<?, ?>> conduit, int x) {
        return place(helper, conduit, new BlockPos(x, 1, 2));
    }

    static ConduitBundleBlockEntity place(GameTestHelper helper, Holder<Conduit<?, ?>> conduit, BlockPos relativePos) {
        var pos = helper.absolutePos(relativePos);
        var stack = ConduitBlockItem.getStackFor(conduit, 1);
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var result = ((ConduitBlockItem) stack.getItem()).place(new BlockPlaceContext(helper.getLevel(), player,
            InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        helper.assertTrue(result.consumesAction(), "Native conduit item placement failed at " + relativePos);
        var blockEntity = helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(blockEntity instanceof ConduitBundleBlockEntity, "Conduit item did not create a bundle");
        var bundle = (ConduitBundleBlockEntity) blockEntity;
        helper.assertTrue(bundle.hasConduitStrict(conduit), "Conduit item variant was lost during placement");
        return bundle;
    }

    private static INetworkNode rsNode(GameTestHelper helper, int x) {
        var blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x, 1, 2)));
        helper.assertTrue(blockEntity != null, "Missing RS endpoint at " + x);
        var proxy = blockEntity.getCapability(NetworkNodeProxyCapability.NETWORK_NODE_PROXY_CAPABILITY).orElse(null);
        helper.assertTrue(proxy != null, "Missing native RS node proxy at " + x);
        return proxy.getNode();
    }

    @GameTest(template = "network", timeoutTicks = 160)
    public static void rsControllerConnectsSplitsAndReconnectsThroughPlacedConduits(GameTestHelper helper) {
        var conduit = rs(helper);
        var removed = new AtomicReference<INetworkNode>();
        helper.startSequence()
            .thenExecute(() -> {
                helper.setBlock(1, 1, 2, RSBlocks.CREATIVE_CONTROLLER.get(DyeColor.LIGHT_BLUE).get());
                helper.setBlock(5, 1, 2, RSBlocks.DISK_DRIVE.get());
                for (int x = 2; x <= 4; x++) place(helper, conduit, x);
                removed.set(rsNode(helper, 3));
            })
            .thenIdle(10)
            .thenExecute(() -> {
                var network = rsNode(helper, 1).getNetwork();
                helper.assertTrue(network != null, "Controller has no native network");
                for (int x = 2; x <= 5; x++) {
                    helper.assertTrue(rsNode(helper, x).getNetwork() == network, "Native RS network did not reach " + x);
                }
            })
            .thenExecute(() -> helper.setBlock(3, 1, 2, Blocks.AIR))
            .thenIdle(5)
            .thenExecute(() -> {
                helper.assertTrue(rsNode(helper, 2).getNetwork() == rsNode(helper, 1).getNetwork(), "Source side lost its controller");
                helper.assertTrue(rsNode(helper, 4).getNetwork() == null && rsNode(helper, 5).getNetwork() == null,
                    "Removing a conduit did not disconnect the remote RS branch");
                helper.assertTrue(!removed.get().isActive(), "Retained removed node still active");
                for (var side : Direction.values()) helper.assertTrue(!removed.get().canReceive(side), "Removed node accepts a connection");
            })
            .thenExecute(() -> place(helper, conduit, 3))
            .thenIdle(10)
            .thenExecute(() -> {
                var network = rsNode(helper, 1).getNetwork();
                helper.assertTrue(rsNode(helper, 5).getNetwork() == network, "Remote disk drive did not reconnect");
                helper.assertTrue(rsNode(helper, 3) != removed.get(), "Replacement reused a detached node");
                var manager = com.refinedmods.refinedstorage.apiimpl.API.instance().getNetworkNodeManager(helper.getLevel());
                helper.assertTrue(manager.getNode(helper.absolutePos(new BlockPos(3, 1, 2))) == rsNode(helper, 3),
                    "Native node manager does not own the replacement");
            })
            .thenSucceed();
    }
    private static appeng.api.networking.IGridNode meNode(GameTestHelper helper, Holder<Conduit<?, ?>> conduit, int x) {
        var bundle = (ConduitBundleBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(x, 1, 2)));
        helper.assertTrue(bundle != null, "Missing ME bundle at " + x);
        var managed = bundle.getConduitNode(conduit)
            .getOrCreateNodeData(com.enderio.modded_conduits.common.modules.appeng.MEConduitNodeData.TYPE).getMainNode();
        helper.assertTrue(managed != null && managed.getNode() != null, "Managed AE2 node is not ready at " + x);
        return managed.getNode();
    }

    @GameTest(template = "network", timeoutTicks = 160)
    public static void authoredMEColorsKeepAdjacentGridsSeparateUntilColorsMatch(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(EnderIORegistries.Keys.CONDUIT);
        var red = registry.getHolderOrThrow(ResourceKey.create(EnderIORegistries.Keys.CONDUIT,
            ResourceLocation.parse("enderio_modded_conduits_tests:red_me")));
        var blue = registry.getHolderOrThrow(ResourceKey.create(EnderIORegistries.Keys.CONDUIT,
            ResourceLocation.parse("enderio_modded_conduits_tests:blue_me")));
        var leftPos = new BlockPos(2, 1, 2);
        var rightPos = leftPos.east();

        helper.startSequence()
            .thenExecute(() -> {
                place(helper, red, 2);
                place(helper, blue, 3);
            })
            .thenIdle(10)
            .thenExecute(() -> {
                var left = meNode(helper, red, 2);
                var right = meNode(helper, blue, 3);
                helper.assertTrue(left.getGridColor() == AEColor.RED && right.getGridColor() == AEColor.BLUE,
                    "The data pack colors did not reach the AE2 nodes");
                helper.assertTrue(left.getGrid() != right.getGrid() && left.getConnections().isEmpty()
                    && right.getConnections().isEmpty(), "Different ME colors connected");
            })
            .thenExecute(() -> {
                helper.setBlock(rightPos, Blocks.AIR);
                place(helper, red, 3);
            })
            .thenIdle(10)
            .thenExecute(() -> {
                var left = meNode(helper, red, 2);
                var right = meNode(helper, red, 3);
                var link = left.getInWorldConnections().get(Direction.EAST);
                helper.assertTrue(right.getGridColor() == AEColor.RED && left.getGrid() == right.getGrid()
                    && link != null && link.getOtherSide(left) == right, "Matching ME colors did not form a native link");
            })
            .thenSucceed();
    }

    @GameTest(template = "network", timeoutTicks = 160)
    public static void meConduitsJoinNativeGridsSplitAndMergeOnReplacement(GameTestHelper helper) {
        var conduit = helper.getLevel().registryAccess().registryOrThrow(EnderIORegistries.Keys.CONDUIT)
            .getHolderOrThrow(com.enderio.modded_conduits.common.modules.appeng.AE2ConduitsModule.NORMAL);
        var removed = new AtomicReference<appeng.api.networking.IGridNode>();
        helper.startSequence()
            .thenExecute(() -> { for (int x = 2; x <= 4; x++) place(helper, conduit, x); })
            .thenIdle(10)
            .thenExecute(() -> {
                var left = meNode(helper, conduit, 2);
                var middle = meNode(helper, conduit, 3);
                var right = meNode(helper, conduit, 4);
                helper.assertTrue(left.getGrid() == middle.getGrid() && middle.getGrid() == right.getGrid(),
                    "Adjacent ME conduits did not join the same native AE2 grid");
                helper.assertTrue(middle.getConnections().size() == 2, "Middle ME node lacks its two physical connections");
                removed.set(middle);
            })
            .thenExecute(() -> helper.setBlock(3, 1, 2, Blocks.AIR))
            .thenIdle(5)
            .thenExecute(() -> {
                helper.assertTrue(meNode(helper, conduit, 2).getGrid() != meNode(helper, conduit, 4).getGrid(),
                    "Removing an ME conduit did not split the native grids");
                helper.assertTrue(removed.get().getConnections().isEmpty(), "Destroyed native ME node retains connections");
            })
            .thenExecute(() -> place(helper, conduit, 3))
            .thenIdle(10)
            .thenExecute(() -> {
                helper.assertTrue(meNode(helper, conduit, 2).getGrid() == meNode(helper, conduit, 4).getGrid(),
                    "Replacing the ME conduit did not merge native grids");
                helper.assertTrue(meNode(helper, conduit, 3) != removed.get(), "ME replacement reused a destroyed node");
            })
            .thenSucceed();
    }

    @GameTest(template = "network", timeoutTicks = 160)
    public static void meConduitsPowerNativeEndpoints(GameTestHelper helper) {
        var level = helper.getLevel();
        var conduit = level.registryAccess().registryOrThrow(EnderIORegistries.Keys.CONDUIT)
            .getHolderOrThrow(com.enderio.modded_conduits.common.modules.appeng.AE2ConduitsModule.NORMAL);
        helper.startSequence()
            .thenExecute(() -> {
                helper.setBlock(1, 1, 2, AEBlocks.CREATIVE_ENERGY_CELL.block());
                helper.setBlock(5, 1, 2, AEBlocks.DRIVE.block());
            })
            .thenIdle(5)
            .thenExecute(() -> {
                for (int x = 2; x <= 4; x++) place(helper, conduit, x);
            })
            .thenIdle(12)
            .thenExecute(() -> {
                var source = GridHelper.getExposedNode(level, helper.absolutePos(new BlockPos(1, 1, 2)), Direction.EAST);
                var drive = GridHelper.getExposedNode(level, helper.absolutePos(new BlockPos(5, 1, 2)), Direction.WEST);
                helper.assertTrue(source != null && drive != null, "The native AE2 endpoints lack exposed grid nodes");
                helper.assertTrue(source.getGrid() == drive.getGrid() && drive.isActive(),
                    "Native creative energy cell did not power the drive through the conduit route");
            })
            .thenSucceed();
    }

    @GameTest(template = "network", timeoutTicks = 160)
    public static void rsDisabledFaceStopsNativeTraversalAndForcedConnectionRestoresIt(GameTestHelper helper) {
        var conduit = rs(helper);
        var bundle = new AtomicReference<ConduitBundleBlockEntity>();
        helper.startSequence()
            .thenExecute(() -> {
                helper.setBlock(1, 1, 2, RSBlocks.CREATIVE_CONTROLLER.get(DyeColor.LIGHT_BLUE).get());
                helper.setBlock(3, 1, 2, RSBlocks.DISK_DRIVE.get());
                bundle.set(place(helper, conduit, 2));
            })
            .thenIdle(10)
            .thenExecute(() -> helper.assertTrue(rsNode(helper, 3).getNetwork() == rsNode(helper, 1).getNetwork(),
                "Initial RS endpoint did not connect"))
            .thenExecute(() -> bundle.get().setConnectionStatus(conduit, Direction.EAST,
                com.enderio.enderio.api.conduits.connection.ConnectionStatus.DISABLED))
            .thenIdle(5)
            .thenExecute(() -> {
                helper.assertTrue(rsNode(helper, 3).getNetwork() == null, "Disabled conduit face still carries native RS network");
                helper.assertTrue(!rsNode(helper, 2).canConduct(Direction.EAST) && !rsNode(helper, 2).canReceive(Direction.EAST),
                    "Disabled face still accepts native traversal");
                helper.assertTrue(bundle.get().tryConnectTo(conduit, Direction.EAST, true), "Forced RS reconnection failed");
            })
            .thenIdle(5)
            .thenExecute(() -> helper.assertTrue(rsNode(helper, 3).getNetwork() == rsNode(helper, 1).getNetwork(),
                "RS endpoint did not reconnect after re-enabling face"))
            .thenSucceed();
    }

    private static void reloadBundle(GameTestHelper helper, Holder<Conduit<?, ?>> conduit, int x) {
        var pos = helper.absolutePos(new BlockPos(x, 1, 2));
        var level = helper.getLevel();
        var original = (ConduitBundleBlockEntity) level.getBlockEntity(pos);
        var tag = original.saveWithFullMetadata();
        var state = original.getBlockState();
        original.onChunkUnloaded();
        level.removeBlockEntity(pos);
        var restored = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, state, tag);
        helper.assertTrue(restored instanceof ConduitBundleBlockEntity && restored != original,
            "Native block-entity loader did not construct a fresh bundle");
        level.setBlockEntity(restored);
        // Forge's normal pending block-entity processing invokes onLoad on the next tick.
    }

    @GameTest(template = "network", timeoutTicks = 180)
    public static void meNativeBlockEntityReloadPreservesOwnerAndReconnectsGrid(GameTestHelper helper) {
        var conduit = helper.getLevel().registryAccess().registryOrThrow(EnderIORegistries.Keys.CONDUIT)
            .getHolderOrThrow(com.enderio.modded_conduits.common.modules.appeng.AE2ConduitsModule.NORMAL);
        var oldNode = new AtomicReference<appeng.api.networking.IGridNode>();
        var oldCapability = new AtomicReference<net.minecraftforge.common.util.LazyOptional<appeng.api.networking.IInWorldGridNodeHost>>();
        var owner = new AtomicReference<java.util.UUID>();
        helper.startSequence()
            .thenExecute(() -> { for (int x = 2; x <= 4; x++) place(helper, conduit, x); })
            .thenIdle(10)
            .thenExecute(() -> {
                var node = meNode(helper, conduit, 3);
                oldNode.set(node);
                owner.set(node.getOwningPlayerProfileId());
                helper.assertTrue(owner.get() != null, "Placed ME node has no owner to persist");
                var bundle = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 1, 2)));
                oldCapability.set(bundle.getCapability(appeng.capabilities.Capabilities.IN_WORLD_GRID_NODE_HOST));
                helper.assertTrue(oldCapability.get().isPresent(), "ME capability was not attached before reload");
                reloadBundle(helper, conduit, 3);
            })
            .thenIdle(10)
            .thenExecute(() -> {
                var node = meNode(helper, conduit, 3);
                helper.assertTrue(node != oldNode.get(), "Reload reused a destroyed native grid node");
                helper.assertTrue(owner.get().equals(node.getOwningPlayerProfileId()), "ME owner did not survive native bundle reload");
                helper.assertTrue(node.getGrid() == meNode(helper, conduit, 2).getGrid()
                    && node.getGrid() == meNode(helper, conduit, 4).getGrid(), "Reloaded ME bundle did not rejoin neighbors");
                helper.assertTrue(!oldCapability.get().isPresent(), "Old ME capability remained valid after native removal");
            })
            .thenSucceed();
    }

    @GameTest(template = "network", timeoutTicks = 180)
    public static void rsNativeBlockEntityReloadPreservesOwnerAndReconnectsController(GameTestHelper helper) {
        var conduit = rs(helper);
        var owner = java.util.UUID.randomUUID();
        var oldNode = new AtomicReference<INetworkNode>();
        helper.startSequence()
            .thenExecute(() -> {
                helper.setBlock(1, 1, 2, RSBlocks.CREATIVE_CONTROLLER.get(DyeColor.LIGHT_BLUE).get());
                helper.setBlock(3, 1, 2, RSBlocks.DISK_DRIVE.get());
                place(helper, conduit, 2);
            })
            .thenIdle(10)
            .thenExecute(() -> {
                oldNode.set(rsNode(helper, 2));
                oldNode.get().setOwner(owner);
                helper.assertTrue(rsNode(helper, 3).getNetwork() == rsNode(helper, 1).getNetwork(), "Initial RS route missing");
                reloadBundle(helper, conduit, 2);
            })
            .thenIdle(10)
            .thenExecute(() -> {
                var node = rsNode(helper, 2);
                helper.assertTrue(node != oldNode.get() && !oldNode.get().isActive(), "Reload reused or retained the old RS node");
                helper.assertTrue(owner.equals(node.getOwner()), "RS owner did not survive native bundle reload");
                helper.assertTrue(node.getNetwork() == rsNode(helper, 1).getNetwork()
                    && rsNode(helper, 3).getNetwork() == node.getNetwork(), "Reloaded RS bundle did not restore controller route");
            })
            .thenSucceed();
    }

}
