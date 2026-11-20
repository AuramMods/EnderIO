package com.enderio.enderio.content.conduits.type.redstone;

import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.api.conduits.ConduitType;
import com.enderio.enderio.api.conduits.network.ConduitNetwork;
import com.enderio.enderio.api.conduits.ticker.ConduitTickerBase;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOConduitTypes;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

public class RedstoneConduitTicker extends ConduitTickerBase<RedstoneConduit> {

    public static final RedstoneConduitTicker INSTANCE = new RedstoneConduitTicker();

    private RedstoneConduitTicker() {
        super(EIOConduitTypes.REDSTONE::get);
    }

    @Override
    protected void tickNetwork(ServerLevel level, ConduitNetwork network, List<Holder<Conduit<?, ?>>> tickableConduits) {
        var context = network.getOrCreateContext(RedstoneConduitNetworkContext.TYPE);
        boolean isActiveBeforeTick = context.isActive();
        context.nextTick();

        for (var channel : network.allChannels()) {
            // Receive input signals.
            for (var extractConnection : network.extractConnections(channel)) {
                int signal;

                var filterStack = extractConnection.inventory().getStackInSlot(RedstoneConduit.EXTRACT_FILTER_SLOT);
                var redstoneExtractFilter = filterStack.getCapability(EnderIOCapabilities.REDSTONE_EXTRACT_FILTER).orElse(null);

                if (redstoneExtractFilter != null) {
                    var previousTag = filterStack.getTag();
                    signal = redstoneExtractFilter.getInputSignal(level, extractConnection.connectedBlockPos(),
                        extractConnection.connectionSide());
                    // ItemDataKey replaces the tag when a timer advances, without changing the inventory slot.
                    if (filterStack.getTag() != previousTag) {
                        level.blockEntityChanged(extractConnection.node().pos());
                    }
                } else {
                    signal = level.getSignal(extractConnection.connectedBlockPos(), extractConnection.connectionSide());
                }

                if (signal > 0) {
                    context.setSignal(channel, signal);
                }
            }
        }

        if (context.activeChannelsChanged()) {
            for (var node : network.tickingNodes()) {
                if (level.getBlockEntity(node.pos()) instanceof ConduitBundleBlockEntity bundle) {
                    bundle.updateRedstoneRouting();
                }
            }
        }

        // Advance stateful filters using one complete sample, before any receiver queries their output.
        boolean outputsChanged = false;
        for (var insertConnection : network.insertConnections()) {
            var filterStack = insertConnection.inventory().getStackInSlot(RedstoneConduit.INSERT_FILTER_SLOT);
            var filter = filterStack.getCapability(EnderIOCapabilities.REDSTONE_INSERT_FILTER).orElse(null);
            if (filter != null) {
                var before = filterStack.getTag();
                var config = insertConnection.connectionConfig(RedstoneConduitConnectionConfig.TYPE);
                filter.updateOutput(context, config.insertChannel());
                if (filterStack.getTag() != before) {
                    level.blockEntityChanged(insertConnection.node().pos());
                    outputsChanged = true;
                }
            }
        }

        // Bundled receivers and filters can observe channels other than their configured output channel.
        if (context.isNew() || context.signalsChanged() || outputsChanged) {
            for (var insertConnection : network.insertConnections()) {
                level.updateNeighborsAt(insertConnection.node().pos(), EIOBlocks.CONDUIT_BUNDLE.get());

                if (insertConnection.connectionConfig(RedstoneConduitConnectionConfig.TYPE).isStrongOutputSignal()) {
                    level.updateNeighborsAt(insertConnection.connectedBlockPos(), EIOBlocks.CONDUIT_BUNDLE.get());
                }
            }
        }

        // Mark all nodes as dirty if the active state changes (update block models).
        if (context.isNew() || context.isActive() != isActiveBeforeTick) {
            for (var node : network.tickingNodes()) {
                node.markDirty();
            }
        }
    }
}
