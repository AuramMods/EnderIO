package com.enderio.enderio.content.machines.capacitor_bank;

import com.enderio.core.common.util.LongMathUtil;
import com.enderio.core.common.graph.Network;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.foundation.network.packets.ClientBoundSyncCapacitorBankPacket;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.energy.IEnergyStorage;
import com.enderio.enderio.foundation.network.EIONetwork;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CapacitorBankNetwork extends Network<CapacitorBankNetwork, CapacitorBankNode> {

    public static final int AVERAGE_IO_OVER_X_TICKS = 10;

    public static final Codec<CapacitorBankNetwork> CODEC = RecordCodecBuilder.create(i ->
        i.group(Codec.LONG.fieldOf("energy").forGetter(c -> c.totalEnergyStored),
                RedstoneControl.CODEC.fieldOf("redstone").forGetter(c -> c.redstoneControl))
            .and(graphCodec(i, CapacitorBankNode.CODEC))
            .apply(i, CapacitorBankNetwork::new));

    private UUID uuid = UUID.randomUUID();
    private RedstoneControl redstoneControl = RedstoneControl.ALWAYS_ACTIVE;

    private long totalEnergyStored;
    private long totalMaxEnergyStored;
    private long energyToDistribute;
    private List<BlockPos> positions = new ArrayList<>();
    private long lastSync;

    //Map to keep track if IO for each position and side
    private final Map<SidedPos, Long> energyIO = new HashMap<>();

    public CapacitorBankNetwork(CapacitorBankNode initialNode, RedstoneControl redstoneControl) {
        super(initialNode);
        this.redstoneControl = redstoneControl;
        this.positions.add(initialNode.getPos());
    }

    public CapacitorBankNetwork(long totalEnergyStored, RedstoneControl redstone, List<CapacitorBankNode> nodes, IndexedEdgeList edges) {
        super(nodes, edges);
        this.totalEnergyStored = totalEnergyStored;
        this.totalMaxEnergyStored = nodes.stream().mapToLong(CapacitorBankNode::getCapacity).sum(); //Load all nodes with the last know capacity
        this.redstoneControl = redstone;
        this.positions.addAll(nodes.stream().map(CapacitorBankNode::getPos).toList());
    }

    public CapacitorBankNetwork() {
        super();
    }

    @Override
    protected CapacitorBankNetwork createEmpty() {
        return new CapacitorBankNetwork();
    }

    public long getTotalEnergyStored() {
        return totalEnergyStored;
    }

    public long getTotalMaxEnergyStored() {
        return totalMaxEnergyStored;
    }

    public void prepareEnergyToDistribute() {
        this.energyToDistribute = getTotalEnergyStored();
    }

    private long getEnergyToDistribute() {
        return energyToDistribute;
    }

    public List<BlockPos> positions() {
        return positions;
    }

    public long getAddedEnergy() {
        long added = 0;
        for (long io : energyIO.values()) {
            if (io > 0) {
                added += io;
            }
        }
        return added;
    }

    public long getSendEnergy() {
        long send = 0;
        for (long io : energyIO.values()) {
            if (io < 0) {
                send -= io;
            }
        }
        return send;
    }

    public void reset(long time) {
        if (lastSync != time) {
            lastSync = time;
            energyIO.clear();
        }
    }

    public RedstoneControl getRedstoneControl() {
        return redstoneControl;
    }

    public void setRedstoneControl(RedstoneControl redstoneControl) {
        this.redstoneControl = redstoneControl;
        for (CapacitorBankNode n : nodes()) {
            if (n.hasBlockEntity()) {
                n.getBlockEntity().setRedstoneControl(redstoneControl);
            }
        }
    }

    public boolean isRedstoneBlocked() {
        return switch (redstoneControl) {
            case ALWAYS_ACTIVE -> false;
            case ACTIVE_WITH_SIGNAL -> {
                for (CapacitorBankNode n : nodes()) {
                    if (n.hasBlockEntity() && !n.getBlockEntity().isRedstoneBlocked()) {
                        yield false;
                    }
                }
                yield true;
            }
            case ACTIVE_WITHOUT_SIGNAL -> {
                for (CapacitorBankNode n : nodes()) {
                    if (n.hasBlockEntity() && n.getBlockEntity().isRedstoneBlocked()) {
                        yield true;
                    }
                }
                yield false;
            }
            case NEVER_ACTIVE -> true;
        };
    }

    public int receiveEnergy(BlockPos pos, @Nullable Direction side, int amount, boolean simulate) {
        long energyBefore = getTotalEnergyStored();
        int result = (int) Math.min(Math.max(0, amount), Math.max(0, getTotalMaxEnergyStored() - energyBefore));

        if (!simulate) {
            this.totalEnergyStored += result;
            if (side != null) {
                long io = energyIO.getOrDefault(new SidedPos(pos, side), 0L);
                energyIO.put(new SidedPos(pos, side), io + result);
            }
        }

        return result;
    }

    public int extractEnergy(BlockPos pos, @Nullable Direction side, int amount, boolean simulate) {
        long extracted = Math.min(getTotalEnergyStored(), Math.max(0, amount));

        if (!simulate) {
            this.totalEnergyStored -= extracted;
            if (side != null) {
                long io = energyIO.getOrDefault(new SidedPos(pos, side), 0L);
                energyIO.put(new SidedPos(pos, side), io - extracted);
            }
        }

        return Math.toIntExact(extracted);
    }

    /**
     * The removed block carries its share. A full array needs to export enough energy
     * to fit its remaining capacity, even when the removed block is a larger tier.
     */
    public int getEnergyForNode(CapacitorBankNode node) {
        long capacity = node.getCapacity();
        long fairShare = totalEnergyStored / nodeCount();
        long required = totalEnergyStored - (totalMaxEnergyStored - capacity);
        return (int) Math.min(capacity, Math.max(fairShare, required));
    }

    public void tick(ServerLevel serverLevel) {
        distributeEnergy();

        //Update client IO
        if (serverLevel.getGameTime() % AVERAGE_IO_OVER_X_TICKS == 0) {
            EIONetwork.sendToPlayersInDimension(serverLevel,
                new ClientBoundSyncCapacitorBankPacket(this.getUuid(), this.getTotalEnergyStored(),
                    this.getTotalMaxEnergyStored(), this.getAddedEnergy()  / AVERAGE_IO_OVER_X_TICKS,
                    this.getSendEnergy() / AVERAGE_IO_OVER_X_TICKS, this.positions()));
            this.reset(serverLevel.getGameTime());
        }
    }

    private long distributeEnergyEvenlyBetween(long availableEnergy, List<CapacitorBankBlockEntity.SidedEnergy> receivers) {
        // Abort if we have no valid pairs
        if (receivers.isEmpty()) {
            return 0;
        }

        // Distribute evenly
        long energyRemaining = availableEnergy;
        int toShareWith = receivers.size();

        for (var sided : receivers) {
            IEnergyStorage receiver = sided.storage();
            // If we have too little energy left, just give it to the first handler that will accept it all
            long shareAmount;
            if (energyRemaining <= toShareWith) {
                shareAmount = energyRemaining;
            } else {
                shareAmount = energyRemaining / toShareWith;
            }

            int inserted = receiver.receiveEnergy(LongMathUtil.nonNegativeInt(shareAmount), false);
            long io = this.energyIO.getOrDefault(sided.sidedPos(), 0L);
            this.energyIO.put(sided.sidedPos(), io - inserted);
            energyRemaining -= inserted;

            toShareWith--;
            if (energyRemaining <= 0) {
                break;
            }
        }

        return availableEnergy - energyRemaining;
    }

    public void distributeEnergy() {
        if (isRedstoneBlocked()) {
            return; //Don't do anything if redstone is blocked
        }

        //Sorted list, so we push to the emptiest first
        List<CapacitorBankBlockEntity.SidedEnergy> validTargets = new ArrayList<>();

        for (CapacitorBankNode node : nodes()) {
            if (node.hasBlockEntity()) {
                validTargets.addAll(node.getBlockEntity().getValidPushTargets());
            }
        }

        validTargets.sort(Comparator.comparingInt(
            c -> c.storage().getMaxEnergyStored() - c.storage().getEnergyStored()
        ));

        long transferred = distributeEnergyEvenlyBetween(Math.min(getEnergyToDistribute(), getTotalEnergyStored()), validTargets);
        this.totalEnergyStored = getTotalEnergyStored() - transferred;
    }

    public void markDirty() {
        nodes().forEach(CapacitorBankNode::markDirty);
    }

    public UUID getUuid() {
        return uuid;
    }

    @Override
    protected void onNodeRemoved(CapacitorBankNode node) {
        refreshTopology();
    }

    @Override
    protected void onNodeAdded(CapacitorBankNode node) {
        totalMaxEnergyStored += node.getCapacity();
        if (positions == null) { //This is called in init when it's not ready yet, so just wait
            return;
        }

        positions.add(node.getPos());
    }

    void updateCapacity(long delta) {
        totalMaxEnergyStored += delta;
    }

    @Override
    protected void onMerged(CapacitorBankNetwork other) {
        totalEnergyStored += other.totalEnergyStored;
        other.totalEnergyStored = 0;
        refreshTopology();
        setRedstoneControl(redstoneControl);
        markDirty();
    }

    @Override
    protected void onGraphSplit(Set<CapacitorBankNetwork> newGraphs) {
        refreshTopology();
        long remainingCapacity = totalMaxEnergyStored
            + newGraphs.stream().mapToLong(CapacitorBankNetwork::getTotalMaxEnergyStored).sum();
        long remainingEnergy = totalEnergyStored;
        for (CapacitorBankNetwork fragment : newGraphs) {
            long capacity = fragment.getTotalMaxEnergyStored();
            long share = BigInteger.valueOf(remainingEnergy).multiply(BigInteger.valueOf(capacity))
                .divide(BigInteger.valueOf(remainingCapacity)).longValueExact();
            fragment.totalEnergyStored = share;
            remainingEnergy -= share;
            remainingCapacity -= capacity;
            fragment.setRedstoneControl(redstoneControl);
            fragment.markDirty();
        }
        totalEnergyStored = remainingEnergy;
        markDirty();
    }

    private void refreshTopology() {
        totalMaxEnergyStored = nodes().stream().mapToLong(CapacitorBankNode::getCapacity).sum();
        positions.clear();
        nodes().forEach(node -> positions.add(node.getPos()));
    }

    public record SidedPos(BlockPos pos, @Nullable Direction side) {}
}
