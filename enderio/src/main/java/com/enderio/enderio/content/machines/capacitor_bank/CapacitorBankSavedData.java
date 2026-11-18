package com.enderio.enderio.content.machines.capacitor_bank;

import com.enderio.enderio.foundation.network.packets.ClientBoundRemoveCapacitorBankPacket;
import com.google.common.base.Preconditions;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import com.enderio.enderio.foundation.network.EIONetwork;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = "enderio", bus = EventBusSubscriber.Bus.FORGE)
public class CapacitorBankSavedData extends SavedData {

    public static final Codec<CapacitorBankSavedData> CODEC = CapacitorBankNetwork.CODEC.listOf()
        .xmap(CapacitorBankSavedData::new, capacitorBankSavedData -> capacitorBankSavedData.getNetworks().stream().toList());

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String KEY_DATA = "Networks";

    private final Set<CapacitorBankNetwork> networks = new HashSet<>();
    private final Map<BlockPos, CapacitorBankNode> unclaimedNodes = new HashMap<>();

    public static CapacitorBankSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CapacitorBankSavedData::load, CapacitorBankSavedData::new,
            "enderio_capacitor_bank_network");
    }

    public CapacitorBankSavedData() {}

    private CapacitorBankSavedData(List<CapacitorBankNetwork> networks) {
        this.networks.addAll(networks);
        for (CapacitorBankNetwork network : networks) {
            for (CapacitorBankNode node : network.nodes()) {
                unclaimedNodes.put(node.getPos(), node);
            }
        }
    }

    private Set<CapacitorBankNetwork> getNetworks() {
        return networks;
    }

    @Nullable
    public CapacitorBankNode claimNode(BlockPos pos) {
        return unclaimedNodes.remove(pos);
    }

    void releaseNode(CapacitorBankNode node) {
        unclaimedNodes.put(node.getPos(), node);
    }

    public static void onNetworkCreated(ServerLevel level, CapacitorBankNetwork network) {
        get(level).onNetworkCreated(network);
    }

    private void onNetworkCreated(CapacitorBankNetwork network) {
        Preconditions.checkArgument(network.isValid(), "New network is not valid!");
        if (!networks.contains(network)) {
            networks.add(network);
        }
    }

    public static void onNetworkDiscarded(ServerLevel level, CapacitorBankNetwork network) {
        get(level).onNetworkDiscarded(network, level);
    }

    private void onNetworkDiscarded(CapacitorBankNetwork network, ServerLevel serverLevel) {
        // Allow empty or discarded networks here
        networks.remove(network);
        EIONetwork.sendToPlayersInDimension(serverLevel,
            new ClientBoundRemoveCapacitorBankPacket(network.getUuid()));
    }

    @Override
    public boolean isDirty() {
        return true;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.level instanceof ServerLevel serverLevel) {
            get(serverLevel).tick(serverLevel);
        }
    }

    private void tick(ServerLevel serverLevel) {
        // avoid CME
        for (CapacitorBankNetwork network : networks.stream().toList()) {
            if (!network.isValid() || network.isEmpty()) {
                onNetworkDiscarded(serverLevel, network);
            } else {
                network.prepareEnergyToDistribute();
            }
        }

        for (var network : networks) {
            network.tick(serverLevel);
        }

    }

    private static CapacitorBankSavedData load(CompoundTag compoundTag) {
        var result = CODEC.parse(NbtOps.INSTANCE, compoundTag.get(KEY_DATA));
        result.error().ifPresent(e -> LOGGER.error("Errors loading capacitor bank network data, some networks may be missing: {}", e.message()));
        return result.getOrThrow(true, LOGGER::error);
    }

    @Override
    public CompoundTag save(CompoundTag compoundTag) {
        compoundTag.put(KEY_DATA, CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow(false, LOGGER::error));
        return compoundTag;
    }
}
