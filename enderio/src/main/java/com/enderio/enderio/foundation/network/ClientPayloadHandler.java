package com.enderio.enderio.foundation.network;

import com.enderio.enderio.api.travel.TravelTargetApi;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorBankManager;
import com.enderio.enderio.content.travel.TravelTargetSavedData;
import com.enderio.enderio.foundation.network.packets.ClientBoundRemoveCapacitorBankPacket;
import com.enderio.enderio.foundation.network.packets.ClientBoundSyncCapacitorBankPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundSyncTravelDataPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundTravelTargetRemovedPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundTravelTargetUpdatedPacket;
import net.minecraft.world.entity.player.Player;

public class ClientPayloadHandler {
    private static final ClientPayloadHandler INSTANCE = new ClientPayloadHandler();

    public static ClientPayloadHandler getInstance() {
        return INSTANCE;
    }

    public void handleSyncTravelDataPacket(ClientboundSyncTravelDataPacket packet, Player player) {
        TravelTargetSavedData travelData = TravelTargetSavedData.getTravelData(player.level());
        travelData.loadNBT(player.level().registryAccess(), packet.data());
    }

    public void handleAddTravelTarget(ClientboundTravelTargetUpdatedPacket packet, Player player) {
        var level = player.level();
        TravelTargetApi.INSTANCE.set(level, packet.target());
    }

    public void handleRemoveTravelTarget(ClientboundTravelTargetRemovedPacket packet, Player player) {
        var level = player.level();
        TravelTargetApi.INSTANCE.removeAt(level, packet.pos());
    }

    public void handleSyncCapacitorBankPacket(ClientBoundSyncCapacitorBankPacket packet, Player player) {
        CapacitorBankManager.addData(packet.uuid(), packet.storedEnergy(), packet.capacity(), packet.added(), packet.send(), packet.nodes());
    }

    public void handleRemoveCapacitorBankPacket(ClientBoundRemoveCapacitorBankPacket packet, Player player) {
        CapacitorBankManager.removeData(packet.uuid());
    }
}
