package com.enderio.modded_conduits.common.modules.refinedstorage;

import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.api.conduits.ConduitCapabilityAccessor;
import com.enderio.enderio.api.conduits.ConduitType;
import com.enderio.enderio.api.conduits.connection.ConnectionReader;
import com.enderio.enderio.api.conduits.connection.config.ConnectionConfigType;
import com.enderio.enderio.api.conduits.network.node.ConduitNode;
import com.enderio.enderio.api.conduits.network.node.NodeData;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.refinedmods.refinedstorage.capability.NetworkNodeProxyCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import com.enderio.core.common.serialization.ComponentCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public record RSConduit(ResourceLocation texture, Component description)
        implements Conduit<RSConduit, RSConduitConnectionConfig> {
    public static final MapCodec<RSConduit> CODEC = RecordCodecBuilder.mapCodec(builder -> builder
            .group(ResourceLocation.CODEC.fieldOf("texture").forGetter(RSConduit::texture),
                    ComponentCodecs.COMPONENT.fieldOf("description").forGetter(RSConduit::description))
            .apply(builder, RSConduit::new));

    @Override
    public ConduitType<RSConduit, RSConduitConnectionConfig> type() {
        return RefinedStorageCommonModule.RS_CONDUIT.get();
    }

    @Override
    public boolean hasMenu() {
        return false;
    }

    @Override
    public boolean shouldCheckConnectionsOnNeighborChange() {
        return false;
    }

    @Override
    public boolean canConnectToBlock(Level level, ConduitCapabilityAccessor capabilityAccessor, BlockPos conduitPos, Direction direction) {
        var cap = capabilityAccessor.getSidedCapability(
            NetworkNodeProxyCapability.NETWORK_NODE_PROXY_CAPABILITY, direction);
        return cap != null && cap.getNode().canReceive(direction.getOpposite());

    }

    @Override
    public void onCreated(ConduitNode node, Level level, BlockPos pos, @Nullable Player player) {
        var data = node.getOrCreateNodeData(RSConduitNodeData.TYPE);
        data.initialize(node, level, pos);
    }

    @Override
    public void onRemoved(ConduitNode node, Level level, BlockPos pos) {
        var data = node.getOrCreateNodeData(RSConduitNodeData.TYPE);
        data.remove(level);
    }

    @Override
    public void onChunkUnloaded(ConduitNode node, Level level, BlockPos pos) {
        node.getOrCreateNodeData(RSConduitNodeData.TYPE).unload(level);
    }

    @Override
    public void onConnectionsUpdated(ConduitNode node, Level level, BlockPos pos, Set<Direction> connectedSides) {
        var data = node.getOrCreateNodeData(RSConduitNodeData.TYPE);
        data.update(level, connectedSides);
    }

    @Override
    public NodeData readForge6NodeData(CompoundTag tag) {
        return new RSConduitNodeData(tag);
    }

    @Override
    public @Nullable <TCapability> TCapability proxyCapability(Level level, ConnectionReader connectionReader, @Nullable ConduitNode node,
        Capability<TCapability> capability, @Nullable Direction tContext) {

        if (node != null && capability == NetworkNodeProxyCapability.NETWORK_NODE_PROXY_CAPABILITY) {
            var data = node.getOrCreateNodeData(RSConduitNodeData.TYPE);
            if (data.isAccessible()) {
                // noinspection unchecked
                return (TCapability) data.getProxy();
            }
        }

        return null;
    }

    @Override
    public int compareTo(@NotNull RSConduit o) {
        return 0;
    }
}
