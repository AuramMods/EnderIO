package com.enderio.enderio.content.conduits.type.fluid;

import com.enderio.core.common.capability.EnderFluidUtil;
import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.api.conduits.connection.ConduitBlockConnection;
import com.enderio.enderio.api.conduits.connection.path.ConduitConnectionPath;
import com.enderio.enderio.api.conduits.network.ConduitNetwork;
import com.enderio.enderio.api.conduits.ticker.ConduitTickerBase;
import com.enderio.enderio.init.EIOConduitTypes;
import com.google.common.collect.Maps;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class FluidConduitTicker extends ConduitTickerBase<FluidConduit> {

    public static final FluidConduitTicker INSTANCE = new FluidConduitTicker();

    private FluidConduitTicker() {
        super(EIOConduitTypes.FLUID::get);
    }

    @Override
    protected void tickNetwork(ServerLevel level, ConduitNetwork network, List<Holder<Conduit<?, ?>>> tickableConduits) {
        Map<ConduitConnectionPath, Integer> insertedPerPath = Maps.newHashMap();
        
        for (var channel : network.allChannels()) {
            for (var extractConnection : network.extractConnections(channel)) {
                var insertPaths = network.insertConnectionsFrom(extractConnection);
                if (insertPaths.isEmpty()) {
                    continue;
                }

                final var extractConduit = extractConnection.node().conduit(conduitType());
                final int fluidRate = com.google.common.math.IntMath.saturatedMultiply(
                    Math.max(0, extractConduit.value().transferRatePerTick()), conduitType().getTickRate(extractConduit));

                int remaining = fluidRate;

                var retained = extractConnection.node().getOrCreateNodeData(EIOConduitTypes.NodeData.FLUID.get());
                if (!retained.first(extractConnection.connectionSide()).isEmpty()) {
                    transferRetained(level, retained, remaining, extractConnection, insertPaths, insertedPerPath);
                    // Do not drain a source again in the same update used to empty a retained batch.
                    continue;
                }

                IFluidHandler extractHandler = extractConnection.getSidedCapability(ForgeCapabilities.FLUID_HANDLER);
                if (extractHandler == null) continue;

                for (int i = 0; i < extractHandler.getTanks() && remaining > 0; i++) {
                    if (extractHandler.getFluidInTank(i).isEmpty()) {
                        continue;
                    }

                    FluidStack fluid = extractHandler.getFluidInTank(i);
                    remaining = doFluidTransfer(level, retained, fluid, remaining, extractConnection, insertPaths, insertedPerPath);
                    if (!retained.first(extractConnection.connectionSide()).isEmpty()) break;
                }
            }
        }
    }

    private int doFluidTransfer(ServerLevel level, FluidConduitNodeData retained, FluidStack fluid, int maxTransfer, ConduitBlockConnection extractConnection,
        List<ConduitConnectionPath> insertPaths, Map<ConduitConnectionPath, Integer> insertedPerPath) {
        var extractHandler = Objects
            .requireNonNull(extractConnection.getSidedCapability(ForgeCapabilities.FLUID_HANDLER));

        // Attempt to drain fluid from the target.
        FluidStack extractedFluid = extractHandler.drain(EnderFluidUtil.copyWithAmount(fluid, maxTransfer),
            IFluidHandler.FluidAction.SIMULATE);
        if (extractedFluid.isEmpty()) {
            return maxTransfer;
        }

        // Test the extracted fluid against the target
        var extractFilter = extractConnection.inventory()
            .getStackInSlot(FluidConduit.EXTRACT_FILTER_SLOT)
            .getCapability(EnderIOCapabilities.FLUID_FILTER).orElse(null);

        if (extractFilter != null) {
            extractedFluid = extractFilter.test(extractHandler, extractedFluid);
            if (extractedFluid.isEmpty()) {
                return maxTransfer;
            }
        }

        // Insert into any available blocks
        for (var insertPath : insertPaths) {
            var insertConnection = insertPath.end();
            final int maxInsertSpeed = com.google.common.math.IntMath.saturatedMultiply(
                Math.max(0, insertPath.property(FluidConduit.PATH_MAX_TRANSFER_RATE)),
                conduitType().getTickRate(extractConnection.node().conduit(conduitType())));

            // Calculate remaining 'speed' for this path
            int remaining = Math.min(maxTransfer, maxInsertSpeed - insertedPerPath.getOrDefault(insertPath, 0));
            if (remaining <= 0) {
                continue;
            }

            IFluidHandler insertHandler = insertConnection.getSidedCapability(ForgeCapabilities.FLUID_HANDLER);
            if (insertHandler == null) {
                continue;
            }

            var fluidToInsert = extractedFluid.copy();

            // Limit to path's max speed
            if (fluidToInsert.getAmount() > remaining) {
                fluidToInsert.setAmount(remaining);
            }

            // Test fluid against insert filter.
            var insertFilter = insertConnection.inventory()
                .getStackInSlot(FluidConduit.INSERT_FILTER_SLOT)
                .getCapability(EnderIOCapabilities.FLUID_FILTER).orElse(null);

            if (insertFilter != null) {
                fluidToInsert = insertFilter.test(insertHandler, fluidToInsert);
                if (fluidToInsert.isEmpty()) {
                    continue;
                }
            }

            int promised = Math.min(fluidToInsert.getAmount(), insertHandler.fill(fluidToInsert, IFluidHandler.FluidAction.SIMULATE));
            if (promised <= 0) continue;
            FluidStack drained = extractHandler.drain(EnderFluidUtil.copyWithAmount(fluidToInsert, promised), IFluidHandler.FluidAction.EXECUTE);
            if (drained.isEmpty()) continue;

            // Own the entire extraction before calling the external destination. Forge's transfer helper
            // otherwise forgets any portion rejected during EXECUTE, including output-only sources.
            retained.retain(extractConnection.connectionSide(), drained);
            markChanged(level, extractConnection);
            int inserted = Math.max(0, Math.min(drained.getAmount(), insertHandler.fill(drained.copy(), IFluidHandler.FluidAction.EXECUTE)));
            retained.consume(extractConnection.connectionSide(), inserted);
            maxTransfer -= drained.getAmount();
            extractedFluid.shrink(drained.getAmount());
            insertedPerPath.merge(insertPath, inserted, Integer::sum);

            if (maxTransfer <= 0 || extractedFluid.isEmpty() || !retained.first(extractConnection.connectionSide()).isEmpty()) {
                break;
            }
        }

        return maxTransfer;
    }

    private void transferRetained(ServerLevel level, FluidConduitNodeData retained, int budget,
            ConduitBlockConnection extractConnection, List<ConduitConnectionPath> paths,
            Map<ConduitConnectionPath, Integer> insertedPerPath) {
        var side = extractConnection.connectionSide();
        for (var path : paths) {
            var fluid = retained.first(side);
            if (fluid.isEmpty() || budget <= 0) break;
            int pathRate = com.google.common.math.IntMath.saturatedMultiply(
                Math.max(0, path.property(FluidConduit.PATH_MAX_TRANSFER_RATE)),
                conduitType().getTickRate(extractConnection.node().conduit(conduitType())));
            int remaining = Math.min(budget, pathRate - insertedPerPath.getOrDefault(path, 0));
            if (remaining <= 0) continue;
            var destination = path.end().getSidedCapability(ForgeCapabilities.FLUID_HANDLER);
            if (destination == null) continue;
            var offered = EnderFluidUtil.copyWithAmount(fluid, Math.min(remaining, fluid.getAmount()));
            var filter = path.end().inventory().getStackInSlot(FluidConduit.INSERT_FILTER_SLOT)
                .getCapability(EnderIOCapabilities.FLUID_FILTER).orElse(null);
            if (filter != null) offered = filter.test(destination, offered);
            if (offered.isEmpty()) continue;
            int inserted = Math.max(0, Math.min(offered.getAmount(), destination.fill(offered, IFluidHandler.FluidAction.EXECUTE)));
            if (inserted == 0) continue;
            retained.consume(side, inserted);
            markChanged(level, extractConnection);
            budget -= inserted;
            insertedPerPath.merge(path, inserted, Integer::sum);
        }
    }

    private static void markChanged(ServerLevel level, ConduitBlockConnection connection) {
        var bundle = level.getBlockEntity(connection.node().pos());
        if (bundle != null) bundle.setChanged();
    }
}
