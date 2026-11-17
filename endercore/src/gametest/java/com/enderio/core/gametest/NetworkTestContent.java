package com.enderio.core.gametest;

import com.enderio.core.common.network.RegistryNetworkChannel;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.enderio.foundation.network.packets.ServerboundTimerFilterPacket;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.network.NetworkDirection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NetworkTestContent {
    public record BiomeMessage(Holder<Biome> biome) {}
    public record LargeMessage(byte[] data) {}
    public record Received(Object message, boolean mainThread) {}
    public static final ResourceLocation NAME = id("network");
    public static final RegistryNetworkChannel CHANNEL = new RegistryNetworkChannel(NAME, "test-1");
    public static final Map<UUID, Received> RECEIVED = new ConcurrentHashMap<>();

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("endercore_tests", path); }

    public static void register() {
        CHANNEL.register(ServerboundTimerFilterPacket.ID, ServerboundTimerFilterPacket.class, ServerboundTimerFilterPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> RECEIVED.put(player.getUUID(), new Received(packet, player.getServer().isSameThread())));
        CHANNEL.register(id("biome"), BiomeMessage.class, PacketCodecs.holderRegistry(Registries.BIOME).map(BiomeMessage::new, BiomeMessage::biome),
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> RECEIVED.put(player.getUUID(), new Received(packet, player.getServer().isSameThread())));
        CHANNEL.register(id("client_timer"), ServerboundTimerFilterPacket.class, ServerboundTimerFilterPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_CLIENT, (packet, player) -> { throw new AssertionError("Client handler executed on server"); });
        CHANNEL.register(id("large"), LargeMessage.class, PacketCodecs.BYTE_ARRAY.map(LargeMessage::new, LargeMessage::data),
            NetworkDirection.PLAY_TO_SERVER, (packet, player) -> { throw new AssertionError("Oversized message executed"); });
    }
}
