package com.enderio.enderio.content.machines.solar_panel;

import com.enderio.core.common.graph.Network;
import com.google.common.primitives.Ints;

import java.util.Set;

public final class SolarPanelNetwork extends Network<SolarPanelNetwork, SolarPanelNode> {

    public SolarPanelNetwork(SolarPanelNode initialNode) {
        super(initialNode);

        initialNode.makePrimaryNode();
    }

    private SolarPanelNetwork() {
        super();
    }

    @Override
    protected SolarPanelNetwork createEmpty() {
        return new SolarPanelNetwork();
    }

    public int getTotalEnergyStored() {
        return Ints.saturatedCast(nodes().stream().mapToLong(SolarPanelNode::getEnergyStored).sum());
    }

    public int getTotalMaxEnergyStored() {
        return Ints.saturatedCast(nodes().stream().mapToLong(SolarPanelNode::getMaxEnergyStored).sum());
    }

    @Override
    protected void onNodeRemoved(SolarPanelNode node) {
        super.onNodeRemoved(node);

        // If we removed the 'primary', find a new one.
        if (node.isPrimaryNode()) {
            ensurePrimaryNode();
        }
    }

    @Override
    protected void onGraphSplit(Set<SolarPanelNetwork> newGraphs) {
        super.onGraphSplit(newGraphs);
        // The original leader may have moved into one of the new graphs.
        ensurePrimaryNode();
        newGraphs.forEach(SolarPanelNetwork::ensurePrimaryNode);
    }

    private void ensurePrimaryNode() {
        if (nodes().stream().noneMatch(SolarPanelNode::isPrimaryNode)) {
            nodes().stream().findFirst().ifPresent(SolarPanelNode::makePrimaryNode);
        }
    }
}
