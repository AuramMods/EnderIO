package com.enderio.modded_conduits.common.modules.mekanism.chemical;

import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.api.conduits.connection.ConduitBlockConnection;
import com.enderio.enderio.api.conduits.connection.path.ConduitConnectionPath;
import com.enderio.enderio.api.conduits.network.ConduitNetwork;
import com.enderio.enderio.api.conduits.ticker.ConduitTickerBase;
import com.enderio.modded_conduits.common.modules.mekanism.MekanismModule;
import com.google.common.math.LongMath;
import mekanism.api.Action;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChemicalTicker extends ConduitTickerBase<ChemicalConduit> {

    public static final ChemicalTicker INSTANCE = new ChemicalTicker();

    private ChemicalTicker() {
        super(MekanismModule.TYPE_CHEMICAL::get);
    }

    @Override
    protected void tickNetwork(ServerLevel level, ConduitNetwork network, List<Holder<Conduit<?, ?>>> tickableConduits) {
        Map<ConduitConnectionPath, Long> insertedPerPath = new HashMap<>();

        for (var channel : network.allChannels()) {
            for (var connection : network.extractConnections(channel)) {
                var paths = network.insertConnectionsFrom(connection);
                if (paths.isEmpty()) continue;

                var conduit = connection.node().conduit(conduitType());
                int tickRate = conduitType().getTickRate(conduit);
                long budget = LongMath.saturatedMultiply(Math.max(0, conduit.value().transferRatePerTick()), tickRate);
                if (budget == 0) continue;
                var side = connection.connectionSide();
                var retained = connection.node().getNodeData(MekanismModule.CHEMICAL_NODE_DATA.get());
                if (retained != null && !retained.first(side).isEmpty()) {
                    retry(level, connection, retained, paths, insertedPerPath, budget, tickRate);
                    continue;
                }

                var source = ChemicalHandlerAdapter.find(connection::getSidedCapability);
                if (source == null) continue;
                for (int i = 0; i < source.getChemicalTanks() && budget > 0; i++) {
                    var stack = source.getChemicalInTank(i);
                    if (stack.isEmpty() || (retained != null && !retained.acceptsExtraction(stack))) continue;
                    budget = transfer(level, connection, source, stack, paths, insertedPerPath, budget, tickRate);
                    retained = connection.node().getNodeData(MekanismModule.CHEMICAL_NODE_DATA.get());
                    if (retained != null && !retained.first(side).isEmpty()) break;
                }
            }
        }
    }

    private static long transfer(ServerLevel level, ConduitBlockConnection connection, ChemicalHandlerAdapter source,
            ChemicalResource stack, List<ConduitConnectionPath> paths, Map<ConduitConnectionPath, Long> insertedPerPath,
            long budget, int tickRate) {
        var available = source.extractChemical(stack.copyWithAmount(budget), Action.SIMULATE);
        if (available.isEmpty()) return budget;
        var extractFilter = connection.inventory().getStackInSlot(ChemicalConduit.EXTRACT_FILTER_SLOT)
            .getCapability(MekanismModule.Capabilities.CHEMICAL_FILTER).orElse(null);
        if (extractFilter != null) available = extractFilter.test(source, available);

        for (var path : paths) {
            if (available.isEmpty() || budget <= 0) break;
            long limit = pathBudget(path, insertedPerPath, budget, tickRate);
            if (limit <= 0) continue;
            var destination = ChemicalHandlerAdapter.find(path.end()::getSidedCapability);
            if (destination == null) continue;
            var offer = filteredOffer(path.end(), destination, available, limit);
            if (offer.isEmpty()) continue;
            long promised = acceptedBy(destination, offer, Action.SIMULATE);
            if (promised == 0) continue;
            var drained = source.extractChemical(offer.copyWithAmount(promised), Action.EXECUTE);
            if (drained.isEmpty()) continue;

            var side = connection.connectionSide();
            var retained = connection.node().getOrCreateNodeData(MekanismModule.CHEMICAL_NODE_DATA.get());
            retained.retain(side, drained);
            markChanged(level, connection);
            // Account for the full source extraction even if the destination delivers only part.
            budget -= drained.getAmount();
            available = available.copyWithAmount(Math.max(0, available.getAmount() - drained.getAmount()));
            if (!ChemicalResource.isSameChemical(offer, drained)) break;
            long inserted = acceptedBy(destination, drained, Action.EXECUTE);
            retained.consume(side, inserted);
            insertedPerPath.merge(path, inserted, Long::sum);
            if (inserted < drained.getAmount()) break;
        }
        return budget;
    }

    private static void retry(ServerLevel level, ConduitBlockConnection connection, ChemicalConduitNodeData retained,
            List<ConduitConnectionPath> paths, Map<ConduitConnectionPath, Long> insertedPerPath, long budget,
            int tickRate) {
        var side = connection.connectionSide();
        for (var path : paths) {
            var stack = retained.first(side);
            if (stack.isEmpty() || budget <= 0) break;
            long limit = pathBudget(path, insertedPerPath, budget, tickRate);
            if (limit <= 0) continue;
            var destination = ChemicalHandlerAdapter.find(path.end()::getSidedCapability);
            if (destination == null) continue;
            var offer = filteredOffer(path.end(), destination, stack, limit);
            if (offer.isEmpty()) continue;
            long inserted = acceptedBy(destination, offer, Action.EXECUTE);
            if (inserted == 0) continue;
            retained.consume(side, inserted);
            markChanged(level, connection);
            budget -= inserted;
            insertedPerPath.merge(path, inserted, Long::sum);
        }
    }

    private static ChemicalResource filteredOffer(ConduitBlockConnection connection, ChemicalHandlerAdapter destination,
            ChemicalResource available, long limit) {
        var offer = available.copyWithAmount(Math.min(available.getAmount(), limit));
        var filter = connection.inventory().getStackInSlot(ChemicalConduit.INSERT_FILTER_SLOT)
            .getCapability(MekanismModule.Capabilities.CHEMICAL_FILTER).orElse(null);
        if (filter == null) return offer;
        var filtered = filter.test(destination, offer);
        return ChemicalResource.isSameChemical(available, filtered)
            ? filtered.copyWithAmount(Math.min(filtered.getAmount(), limit)) : ChemicalResource.EMPTY;
    }

    private static long acceptedBy(ChemicalHandlerAdapter destination, ChemicalResource offer, Action action) {
        var remainder = destination.insertChemical(offer, action);
        return Math.max(0, offer.getAmount() - remainder.getAmount());
    }

    private static long pathBudget(ConduitConnectionPath path, Map<ConduitConnectionPath, Long> insertedPerPath,
            long sourceBudget, int tickRate) {
        long rate = LongMath.saturatedMultiply(Math.max(0, path.property(ChemicalConduit.PATH_MAX_TRANSFER_RATE)), tickRate);
        return Math.min(sourceBudget, rate - insertedPerPath.getOrDefault(path, 0L));
    }

    private static void markChanged(ServerLevel level, ConduitBlockConnection connection) {
        var bundle = level.getBlockEntity(connection.node().pos());
        if (bundle != null) bundle.setChanged();
    }
}
