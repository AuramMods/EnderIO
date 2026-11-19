package com.enderio.modded_conduits.common.modules.mekanism.heat;

import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.api.conduits.ConduitType;
import com.enderio.enderio.api.conduits.network.ConduitNetwork;
import com.enderio.enderio.api.conduits.ticker.ConduitTickerBase;
import com.enderio.modded_conduits.common.modules.mekanism.MekanismModule;
import mekanism.api.heat.IHeatHandler;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class HeatTicker extends ConduitTickerBase<HeatConduit> {

    public static final HeatTicker INSTANCE = new HeatTicker();

    private HeatTicker() {
        super(MekanismModule.TYPE_HEAT::get);
    }

    @Override
    protected void tickNetwork(ServerLevel level, ConduitNetwork network, List<Holder<Conduit<?, ?>>> tickableConduits) {
        // Native heat capacitors can defer applying heat until their next update.
        Map<IHeatHandler, Double> heatChanges = new IdentityHashMap<>();
        for (var extractConnection : network.extractConnections()) {
            var insertPaths = network.insertConnectionsFrom(extractConnection);
            if (insertPaths.isEmpty()) {
                continue;
            }

            IHeatHandler extractHandler = extractConnection.getSidedCapability(MekanismModule.Capabilities.HEAT);
            if (extractHandler == null) {
                continue;
            }
            double extractCapacity = extractHandler.getTotalHeatCapacity();
            if (!(extractCapacity > 0)) {
                continue;
            }

            for (var insertPath : insertPaths) {
                var insertConnection = insertPath.end();
                IHeatHandler insertHandler = insertConnection.getSidedCapability(MekanismModule.Capabilities.HEAT);
                if (insertHandler == null || insertHandler == extractHandler) {
                    continue;
                }

                double insertCapacity = insertHandler.getTotalHeatCapacity();
                if (!(insertCapacity > 0)) {
                    continue;
                }
                double extractTemperature = extractHandler.getTotalTemperature() + heatChanges.getOrDefault(extractHandler, 0.0) / extractCapacity;
                double insertTemperature = insertHandler.getTotalTemperature() + heatChanges.getOrDefault(insertHandler, 0.0) / insertCapacity;
                double difference = extractTemperature - insertTemperature;
                if (!(difference > 0)) {
                    continue;
                }

                double invConduction = insertHandler.getTotalInverseConduction()
                    + extractHandler.getTotalInverseConduction();
                // Moving this much heat would leave both endpoint temperatures equal.
                double heatToEquilibrium = difference / (1 / extractCapacity + 1 / insertCapacity);
                double heatToTransfer = Math.min(difference / invConduction * extractCapacity, heatToEquilibrium);
                if (heatToTransfer > 0 && Double.isFinite(heatToTransfer)) {
                    heatChanges.merge(extractHandler, -heatToTransfer, Double::sum);
                    heatChanges.merge(insertHandler, heatToTransfer, Double::sum);
                }
            }
        }
        heatChanges.forEach(IHeatHandler::handleHeat);
    }
}
