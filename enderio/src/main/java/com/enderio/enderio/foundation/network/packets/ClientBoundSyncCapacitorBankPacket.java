package com.enderio.enderio.foundation.network.packets;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorBankBlockEntity;
import net.minecraft.core.BlockPos;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record ClientBoundSyncCapacitorBankPacket(UUID uuid, long storedEnergy, long capacity, long added, long send, List<BlockPos> nodes) {
    public static final ResourceLocation ID = EnderIOAPI.rl("sync_capacitor_bank");

    public static final PacketCodec<ClientBoundSyncCapacitorBankPacket> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.UUID,
        ClientBoundSyncCapacitorBankPacket::uuid,
        PacketCodecs.VAR_LONG,
        ClientBoundSyncCapacitorBankPacket::storedEnergy,
        PacketCodecs.VAR_LONG,
        ClientBoundSyncCapacitorBankPacket::capacity,
        PacketCodecs.VAR_LONG,
        ClientBoundSyncCapacitorBankPacket::added,
        PacketCodecs.VAR_LONG,
        ClientBoundSyncCapacitorBankPacket::send,
        PacketCodecs.BLOCK_POS.list(CapacitorBankBlockEntity.MAX_SIZE),
        ClientBoundSyncCapacitorBankPacket::nodes,
        ClientBoundSyncCapacitorBankPacket::new
    );

}
