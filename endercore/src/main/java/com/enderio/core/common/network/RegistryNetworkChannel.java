package com.enderio.core.common.network;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.codec.RegistryPacketBuffer;
import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** Forge transport that defers registry-dependent decoding until the receiving game thread. */
public final class RegistryNetworkChannel {
    // Native custom-payload limits, less the one-byte SimpleChannel discriminator (0 or 1).
    public static final int MAX_SERVERBOUND_BYTES = 32_766;
    public static final int MAX_CLIENTBOUND_BYTES = 1_048_575;

    private record ServerMessage(byte[] data) {}
    private record ClientMessage(byte[] data) {}
    private record Entry<T>(ResourceLocation id, Class<T> type, PacketCodec<T> codec, BiConsumer<T, Player> handler) {
        void encode(FriendlyByteBuf buffer, Object packet) { codec.encode(buffer, type.cast(packet)); }
        Runnable decode(FriendlyByteBuf buffer, Player player) {
            T packet = codec.decode(buffer);
            return () -> handler.accept(packet, player);
        }
    }

    private final SimpleChannel channel;
    private final Map<NetworkDirection, Map<ResourceLocation, Entry<?>>> byId = new EnumMap<>(NetworkDirection.class);
    private final Map<NetworkDirection, Map<Class<?>, Entry<?>>> byClass = new EnumMap<>(NetworkDirection.class);

    public RegistryNetworkChannel(ResourceLocation name, String protocol) {
        channel = NetworkRegistry.newSimpleChannel(name, () -> protocol, protocol::equals, protocol::equals);
        for (var direction : new NetworkDirection[] {NetworkDirection.PLAY_TO_SERVER, NetworkDirection.PLAY_TO_CLIENT}) {
            byId.put(direction, new HashMap<>());
            byClass.put(direction, new HashMap<>());
        }
        channel.messageBuilder(ServerMessage.class, 0, NetworkDirection.PLAY_TO_SERVER)
            .encoder((message, buffer) -> buffer.writeBytes(message.data()))
            .decoder(buffer -> new ServerMessage(readMessage(buffer, MAX_SERVERBOUND_BYTES)))
            .consumerMainThread((message, context) -> {
                var ctx = context.get();
                var player = ctx.getSender();
                if (player != null && !player.isRemoved() && player.connection.connection == ctx.getNetworkManager()) {
                    receive(message.data(), player, ctx);
                }
            }).add();
        channel.messageBuilder(ClientMessage.class, 1, NetworkDirection.PLAY_TO_CLIENT)
            .encoder((message, buffer) -> buffer.writeBytes(message.data()))
            .decoder(buffer -> new ClientMessage(readMessage(buffer, MAX_CLIENTBOUND_BYTES)))
            .consumerMainThread((message, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientConnection.receive(this, message.data(), context.get()))).add();
    }

    public <T> void register(ResourceLocation id, Class<T> type, PacketCodec<T> codec, NetworkDirection direction,
            BiConsumer<T, Player> handler) {
        var ids = byId.get(direction);
        var classes = byClass.get(direction);
        if (ids == null) throw new IllegalArgumentException("Only play packets are supported");
        if (ids.containsKey(id) || classes.containsKey(type)) throw new IllegalArgumentException("Duplicate packet registration: " + id);
        var entry = new Entry<>(id, type, codec, handler);
        ids.put(id, entry);
        classes.put(type, entry);
    }

    /** Encodes against the sending player's current registry context before native transport. */
    public Packet<?> toVanillaPacket(Object packet, NetworkDirection direction, RegistryAccess registries) {
        var entries = byClass.get(direction);
        Entry<?> entry = entries == null ? null : entries.get(packet.getClass());
        if (entry == null) throw new EncoderException("Unregistered packet/direction: " + packet.getClass() + " / " + direction);
        int maximum = direction == NetworkDirection.PLAY_TO_SERVER ? MAX_SERVERBOUND_BYTES : MAX_CLIENTBOUND_BYTES;
        var buffer = new RegistryPacketBuffer(Unpooled.buffer(256, maximum), registries);
        try {
            buffer.writeResourceLocation(entry.id());
            entry.encode(buffer, packet);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return direction == NetworkDirection.PLAY_TO_SERVER
                ? channel.toVanillaPacket(new ServerMessage(data), direction)
                : channel.toVanillaPacket(new ClientMessage(data), direction);
        } catch (IndexOutOfBoundsException error) {
            throw new EncoderException("Packet exceeds native payload limit: " + entry.id(), error);
        } finally {
            buffer.release();
        }
    }

    public void sendToPlayer(ServerPlayer player, Object packet) {
        player.connection.send(toVanillaPacket(packet, NetworkDirection.PLAY_TO_CLIENT, player.level().registryAccess()));
    }

    public void sendToServer(Object packet) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientConnection.send(this, packet));
    }

    private static byte[] readMessage(FriendlyByteBuf buffer, int maximum) {
        int length = buffer.readableBytes();
        if (length == 0 || length > maximum) throw new DecoderException("Invalid packet payload size: " + length);
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return bytes;
    }

    private void receive(byte[] data, Player player, NetworkEvent.Context context) {
        Runnable handler;
        var buffer = new RegistryPacketBuffer(Unpooled.wrappedBuffer(data), player.level().registryAccess());
        try {
            ResourceLocation id = buffer.readResourceLocation();
            Entry<?> entry = byId.get(context.getDirection()).get(id);
            if (entry == null) throw new DecoderException("Unknown packet/direction: " + id);
            handler = entry.decode(buffer, player);
            if (buffer.isReadable()) throw new DecoderException("Trailing packet bytes: " + id);
        } catch (RuntimeException error) {
            LogUtils.getLogger().warn("Rejecting malformed Ender IO packet", error);
            context.getNetworkManager().disconnect(Component.literal("Invalid Ender IO packet"));
            return;
        } finally {
            buffer.release();
        }
        handler.run();
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientConnection {
        private static void receive(RegistryNetworkChannel channel, byte[] data, NetworkEvent.Context context) {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            var connection = minecraft.getConnection();
            if (minecraft.player != null && connection != null && connection.getConnection() == context.getNetworkManager()) {
                channel.receive(data, minecraft.player, context);
            }
        }

        private static void send(RegistryNetworkChannel channel, Object packet) {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.player == null || minecraft.getConnection() == null) return;
            minecraft.getConnection().getConnection().send(channel.toVanillaPacket(packet,
                NetworkDirection.PLAY_TO_SERVER, minecraft.player.level().registryAccess()));
        }
    }
}
