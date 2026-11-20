package com.enderio.enderio.foundation.network;

import com.enderio.enderio.foundation.network.packets.ClientBoundRemoveCapacitorBankPacket;
import com.enderio.enderio.foundation.network.packets.ClientBoundSyncCapacitorBankPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundConduitExtraGuiDataPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundConduitListPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundFarmStationSoulPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundPoweredSpawnerSoulPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundSolarSoulPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundSoulEngineSoulPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundSyncTravelDataPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundTravelTargetRemovedPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundTravelTargetUpdatedPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundCountFilterPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundCycleIOConfigPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundDoubleChannelPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundEnderfaceInteractPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundOpenConduitFilterMenu;
import com.enderio.enderio.foundation.network.packets.ServerboundRequestShortTravelPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundRequestTravelPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundSetFluidFilterSlotPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundSetGhostSlotPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundSetItemFilterSlotPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundSyncProbeStatePacket;
import com.enderio.enderio.foundation.network.packets.ServerboundTimerFilterPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundToggleMagnetPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundTransferItemsPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundUpdateCoordinateSelectionNameMenuPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundUpdateCrafterTemplatePacket;
import com.enderio.enderio.foundation.network.packets.ServerboundVatRecipeTransferPacket;
import com.enderio.enderio.foundation.network.packets.SetConduitConnectionConfigPacket;
import com.enderio.enderio.foundation.souldata.EngineSoul;
import com.enderio.enderio.foundation.souldata.FarmSoul;
import com.enderio.enderio.foundation.souldata.SolarSoul;
import com.enderio.enderio.foundation.souldata.SpawnerSoul;
import com.enderio.core.common.network.RegistryNetworkChannel;
import com.enderio.enderio.api.EnderIOAPI;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;

@EventBusSubscriber(modid = "enderio", bus = EventBusSubscriber.Bus.MOD)
public final class EIONetwork {
    private static final RegistryNetworkChannel CHANNEL = new RegistryNetworkChannel(EnderIOAPI.rl("main"), "forge-4");

    private EIONetwork() {}

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        SpawnerSoul.SPAWNER.subscribeAsSyncable(ClientboundPoweredSpawnerSoulPacket::new);
        EngineSoul.ENGINE.subscribeAsSyncable(ClientboundSoulEngineSoulPacket::new);
        FarmSoul.FARM.subscribeAsSyncable(ClientboundFarmStationSoulPacket::new);
        SolarSoul.SOLAR.subscribeAsSyncable(ClientboundSolarSoulPacket::new);

        CHANNEL.register(ClientboundSyncTravelDataPacket.ID, ClientboundSyncTravelDataPacket.class, ClientboundSyncTravelDataPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_CLIENT, (packet, player) -> ClientPayloadHandler.getInstance().handleSyncTravelDataPacket(packet, player));
        CHANNEL.register(ClientboundTravelTargetUpdatedPacket.ID, ClientboundTravelTargetUpdatedPacket.class, ClientboundTravelTargetUpdatedPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_CLIENT, (packet, player) -> ClientPayloadHandler.getInstance().handleAddTravelTarget(packet, player));
        CHANNEL.register(ClientboundTravelTargetRemovedPacket.ID, ClientboundTravelTargetRemovedPacket.class, ClientboundTravelTargetRemovedPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_CLIENT, (packet, player) -> ClientPayloadHandler.getInstance().handleRemoveTravelTarget(packet, player));
        CHANNEL.register(ClientBoundSyncCapacitorBankPacket.ID, ClientBoundSyncCapacitorBankPacket.class, ClientBoundSyncCapacitorBankPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_CLIENT, (packet, player) -> ClientPayloadHandler.getInstance().handleSyncCapacitorBankPacket(packet, player));
        CHANNEL.register(ClientBoundRemoveCapacitorBankPacket.ID, ClientBoundRemoveCapacitorBankPacket.class, ClientBoundRemoveCapacitorBankPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_CLIENT, (packet, player) -> ClientPayloadHandler.getInstance().handleRemoveCapacitorBankPacket(packet, player));
        CHANNEL.register(ServerboundUpdateCoordinateSelectionNameMenuPacket.ID, ServerboundUpdateCoordinateSelectionNameMenuPacket.class, ServerboundUpdateCoordinateSelectionNameMenuPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> ServerPayloadHandler.getInstance().handleCoordinateSelectionName(packet, player));
        CHANNEL.register(ServerboundRequestTravelPacket.ID, ServerboundRequestTravelPacket.class, ServerboundRequestTravelPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> ServerPayloadHandler.getInstance().handleTravelRequest(packet, player));
        CHANNEL.register(ServerboundRequestShortTravelPacket.ID, ServerboundRequestShortTravelPacket.class, ServerboundRequestShortTravelPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> ServerPayloadHandler.getInstance().handleShortTravelRequest(packet, player));
        CHANNEL.register(ServerboundSetFluidFilterSlotPacket.ID, ServerboundSetFluidFilterSlotPacket.class, ServerboundSetFluidFilterSlotPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> ServerPayloadHandler.getInstance().handleSetFluidFilterSlot(packet, player));
        CHANNEL.register(ServerboundSetGhostSlotPacket.ID, ServerboundSetGhostSlotPacket.class, ServerboundSetGhostSlotPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> ServerPayloadHandler.getInstance().handleSetGhostSlot(packet, player));
        CHANNEL.register(ServerboundSetItemFilterSlotPacket.ID, ServerboundSetItemFilterSlotPacket.class, ServerboundSetItemFilterSlotPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> ServerPayloadHandler.getInstance().handleSetItemFilterSlot(packet, player));
        CHANNEL.register(ServerboundDoubleChannelPacket.ID, ServerboundDoubleChannelPacket.class, ServerboundDoubleChannelPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> ConduitServerPayloadHandler.getInstance().handleDoubleChannelFilter(packet, player));
        CHANNEL.register(ServerboundTimerFilterPacket.ID, ServerboundTimerFilterPacket.class, ServerboundTimerFilterPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> ConduitServerPayloadHandler.getInstance().handleTimerFilter(packet, player));
        CHANNEL.register(ServerboundToggleMagnetPacket.ID, ServerboundToggleMagnetPacket.class, ServerboundToggleMagnetPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> ServerPayloadHandler.getInstance().handleMagnetToggle(packet, player));
        CHANNEL.register(ServerboundCountFilterPacket.ID, ServerboundCountFilterPacket.class, ServerboundCountFilterPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> ConduitServerPayloadHandler.getInstance().handleCountFilter(packet, player));
        CHANNEL.register(ClientboundConduitExtraGuiDataPacket.ID, ClientboundConduitExtraGuiDataPacket.class, ClientboundConduitExtraGuiDataPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_CLIENT, (packet, player) -> ConduitClientPayloadHandler.getInstance().handle(packet, player));
        CHANNEL.register(ClientboundConduitListPacket.ID, ClientboundConduitListPacket.class, ClientboundConduitListPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_CLIENT, (packet, player) -> ConduitClientPayloadHandler.getInstance().handle(packet, player));
        CHANNEL.register(SetConduitConnectionConfigPacket.ID, SetConduitConnectionConfigPacket.class, SetConduitConnectionConfigPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_CLIENT, (packet, player) -> ConduitCommonPayloadHandler.getInstance().handle(packet, player));
        CHANNEL.register(SetConduitConnectionConfigPacket.ID, SetConduitConnectionConfigPacket.class, SetConduitConnectionConfigPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> ConduitCommonPayloadHandler.getInstance().handle(packet, player));
        CHANNEL.register(ServerboundOpenConduitFilterMenu.ID, ServerboundOpenConduitFilterMenu.class, ServerboundOpenConduitFilterMenu.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> ConduitServerPayloadHandler.getInstance().handle(packet, player));
        CHANNEL.register(ServerboundSyncProbeStatePacket.ID, ServerboundSyncProbeStatePacket.class, ServerboundSyncProbeStatePacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> ConduitServerPayloadHandler.getInstance().handle(packet, player));
        CHANNEL.register(ClientboundPoweredSpawnerSoulPacket.ID, ClientboundPoweredSpawnerSoulPacket.class, ClientboundPoweredSpawnerSoulPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_CLIENT, (packet, player) -> MachinePayloadHandler.Client.getInstance().handlePoweredSpawnerSoul(packet, player));
        CHANNEL.register(ClientboundSoulEngineSoulPacket.ID, ClientboundSoulEngineSoulPacket.class, ClientboundSoulEngineSoulPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_CLIENT, (packet, player) -> MachinePayloadHandler.Client.getInstance().handleSoulEngineSoul(packet, player));
        CHANNEL.register(ClientboundFarmStationSoulPacket.ID, ClientboundFarmStationSoulPacket.class, ClientboundFarmStationSoulPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_CLIENT, (packet, player) -> MachinePayloadHandler.Client.getInstance().handleFarmingStationSoul(packet, player));
        CHANNEL.register(ClientboundSolarSoulPacket.ID, ClientboundSolarSoulPacket.class, ClientboundSolarSoulPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_CLIENT, (packet, player) -> MachinePayloadHandler.Client.getInstance().handleSolarSoul(packet, player));
        CHANNEL.register(ServerboundUpdateCrafterTemplatePacket.ID, ServerboundUpdateCrafterTemplatePacket.class, ServerboundUpdateCrafterTemplatePacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> MachinePayloadHandler.Server.getInstance().updateCrafterTemplate(packet, player));
        CHANNEL.register(ServerboundCycleIOConfigPacket.ID, ServerboundCycleIOConfigPacket.class, ServerboundCycleIOConfigPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> MachinePayloadHandler.Server.getInstance().handleCycleIOConfigPacket(packet, player));
        CHANNEL.register(ServerboundEnderfaceInteractPacket.ID, ServerboundEnderfaceInteractPacket.class, ServerboundEnderfaceInteractPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> MachinePayloadHandler.Server.getInstance().handleEnderfaceInteract(packet, player));
        CHANNEL.register(ServerboundTransferItemsPacket.ID, ServerboundTransferItemsPacket.class, ServerboundTransferItemsPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> MachinePayloadHandler.Server.getInstance().handleTransferItems(packet, player));
        CHANNEL.register(ServerboundVatRecipeTransferPacket.ID, ServerboundVatRecipeTransferPacket.class, ServerboundVatRecipeTransferPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> MachinePayloadHandler.Server.getInstance().handleVatRecipeTransfer(packet, player));
    }

    public static void sendToServer(Object packet) { CHANNEL.sendToServer(packet); }

    public static void sendToPlayer(ServerPlayer player, Object packet) { CHANNEL.sendToPlayer(player, packet); }

    public static void sendToPlayersInDimension(ServerLevel level, Object packet) {
        level.players().forEach(player -> sendToPlayer(player, packet));
    }

    public static void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos chunk, Object packet) {
        level.getChunkSource().chunkMap.getPlayers(chunk, false).forEach(player -> sendToPlayer(player, packet));
    }
}
