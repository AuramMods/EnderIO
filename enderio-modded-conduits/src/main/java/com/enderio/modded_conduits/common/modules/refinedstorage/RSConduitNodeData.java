package com.enderio.modded_conduits.common.modules.refinedstorage;

import com.enderio.core.common.serialization.StrictOptionalFieldCodec;
import com.enderio.enderio.api.conduits.network.node.ConduitNode;
import com.enderio.enderio.api.conduits.network.node.NodeData;
import com.enderio.enderio.api.conduits.network.node.NodeDataType;
import com.mojang.serialization.MapCodec;
import com.refinedmods.refinedstorage.api.network.INetwork;
import com.refinedmods.refinedstorage.api.network.node.INetworkNodeProxy;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.apiimpl.API;
import com.refinedmods.refinedstorage.capability.NetworkNodeProxyCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class RSConduitNodeData implements NodeData {
    public static final MapCodec<RSConduitNodeData> CODEC =
        new StrictOptionalFieldCodec<>(CompoundTag.CODEC, "main_node")
            .xmap(tag -> new RSConduitNodeData(tag.orElseGet(CompoundTag::new)), data -> Optional.of(data.saveNode()));
    public static final NodeDataType<RSConduitNodeData> TYPE = new NodeDataType<>(CODEC, RSConduitNodeData::new);

    private CompoundTag savedNode;
    @Nullable private ConduitRSNode mainNode;
    @Nullable private ConduitRSNode pendingUnload;
    @Nullable private INetworkNodeProxy<ConduitRSNode> proxy;

    public RSConduitNodeData() { this(new CompoundTag()); }
    RSConduitNodeData(CompoundTag savedNode) { this.savedNode = savedNode.copy(); }

    private CompoundTag saveNode() {
        return mainNode == null ? savedNode.copy() : mainNode.write(new CompoundTag());
    }

    public boolean isAccessible() { return mainNode != null && mainNode.isActive(); }
    public @Nullable INetworkNodeProxy<ConduitRSNode> getProxy() { return proxy; }

    public void initialize(ConduitNode conduitNode, Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (mainNode != null) remove(level);
        // RS identifies graph entries by position. Retire any old entry before exposing the replacement.
        finishUnload(serverLevel);
        var manager = API.instance().getNetworkNodeManager(serverLevel);
        var stored = manager.getNode(pos);
        var node = stored instanceof ConduitRSNode existing ? existing : new ConduitRSNode(level, pos);
        if (!(stored instanceof ConduitRSNode)) node.read(savedNode.copy());
        mainNode = node;
        // A retained proxy keeps a detached, inactive node after removal, never a replacement at this position.
        proxy = () -> node;
        node.attach(Arrays.stream(Direction.values()).filter(conduitNode::isConnectedTo).collect(Collectors.toSet()));
        manager.setNode(pos, node);
        manager.markForSaving();
        invalidateNetworks(level, node);
        level.blockUpdated(pos, level.getBlockState(pos).getBlock());
    }

    public void update(Level level, Set<Direction> connectedSides) {
        if (mainNode != null) {
            mainNode.attach(connectedSides);
            invalidateNetworks(level, mainNode);
        }
    }

    public void remove(Level level) {
        remove(level, false);
    }

    public void unload(Level level) {
        remove(level, true);
    }

    private void remove(Level level, boolean unloading) {
        var node = mainNode;
        if (node == null) return;
        savedNode = saveNode();
        node.detach();
        mainNode = null;
        proxy = null;
        if (level instanceof ServerLevel serverLevel) {
            var manager = API.instance().getNetworkNodeManager(serverLevel);
            if (manager.getNode(node.getPos()) == node) {
                manager.removeNode(node.getPos());
                manager.markForSaving();
            }
            if (unloading) {
                // RS walks block entities synchronously, which can resurrect a chunk while it unloads.
                pendingUnload = node;
                var server = serverLevel.getServer();
                server.tell(new TickTask(server.getTickCount(), () -> {
                    if (pendingUnload == node) finishUnload(serverLevel);
                }));
            } else {
                invalidateNetworks(serverLevel, node);
            }
        }
    }

    private void finishUnload(ServerLevel level) {
        var node = pendingUnload;
        pendingUnload = null;
        if (node != null) invalidateNetworks(level, node);
    }

    private static void invalidateNetworks(Level level, ConduitRSNode node) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        var networks = new HashSet<INetwork>();
        if (node.getNetwork() != null) networks.add(node.getNetwork());
        for (var side : Direction.values()) {
            var pos = node.getPos().relative(side);
            var blockEntity = getLoadedBlockEntity(serverLevel, pos);
            if (blockEntity == null) continue;
            var neighbor = blockEntity.getCapability(NetworkNodeProxyCapability.NETWORK_NODE_PROXY_CAPABILITY).orElse(null);
            if (neighbor != null && neighbor.getNode().getNetwork() != null) networks.add(neighbor.getNode().getNetwork());
        }
        for (var network : networks) {
            network.getNodeGraph().runActionWhenPossible(current -> {
                if (current.getLevel() instanceof ServerLevel currentLevel &&
                    getLoadedBlockEntity(currentLevel, current.getPosition()) != null) {
                    current.getNodeGraph().invalidate(Action.PERFORM, currentLevel, current.getPosition());
                }
            });
        }
    }

    private static @Nullable BlockEntity getLoadedBlockEntity(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(
            SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        var blockEntity = chunk == null ? null : chunk.getBlockEntities().get(pos);
        return blockEntity == null || blockEntity.isRemoved() ? null : blockEntity;
    }

    @Override public NodeDataType<?> type() { return TYPE; }
}
