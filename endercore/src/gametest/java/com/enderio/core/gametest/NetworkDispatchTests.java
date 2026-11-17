package com.enderio.core.gametest;

import com.enderio.core.common.network.RegistryNetworkChannel;
import com.enderio.enderio.foundation.network.packets.ServerboundTimerFilterPacket;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.enderio.core.gametest.NetworkTestContent.*;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class NetworkDispatchTests {
    private static final class Peer implements AutoCloseable {
        final ServerPlayer player;
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        final EmbeddedChannel socket = new EmbeddedChannel(connection);
        Peer(GameTestHelper helper) {
            var level = helper.getLevel();
            player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "PacketFixture"));
            new ServerGamePacketListenerImpl(level.getServer(), connection, player);
            connection.setProtocol(ConnectionProtocol.PLAY);
        }
        public void close() {
            RECEIVED.remove(player.getUUID());
            socket.finishAndReleaseAll();
            player.getTextFilter().leave();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void nativeForgeDispatchQueuesActualMainPacketOnServerThread(GameTestHelper helper) {
        var peer = new Peer(helper);
        var expected = new ServerboundTimerFilterPacket(7, 39);
        var packet = (ServerboundCustomPayloadPacket) CHANNEL.toVanillaPacket(expected,
            NetworkDirection.PLAY_TO_SERVER, peer.player.level().registryAccess());
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                if (!NetworkHooks.onCustomPayload(packet, peer.connection)) throw new AssertionError("Native channel did not handle packet");
            } catch (Throwable failure) { error.set(failure); }
            finally { packet.getData().release(); }
        }, "EnderIO packet fixture");
        worker.setDaemon(true);
        worker.start();
        helper.succeedWhen(() -> {
            helper.assertTrue(error.get() == null, "Dispatch failed: " + error.get());
            helper.assertTrue(!worker.isAlive(), "Waiting for native dispatch thread");
            Received received = RECEIVED.get(peer.player.getUUID());
            helper.assertTrue(received != null, "Waiting for queued packet");
            helper.assertTrue(received.mainThread() && expected.equals(received.message()), "Packet escaped server thread or changed fields");
            peer.close();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void dynamicHolderUsesPlayerRegistryThroughNativeChannel(GameTestHelper helper) {
        try (var peer = new Peer(helper)) {
            var holder = peer.player.level().registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
            var packet = (ServerboundCustomPayloadPacket) CHANNEL.toVanillaPacket(new BiomeMessage(holder),
                NetworkDirection.PLAY_TO_SERVER, peer.player.level().registryAccess());
            try {
                helper.assertTrue(NetworkHooks.onCustomPayload(packet, peer.connection), "Native channel was not selected");
                var received = RECEIVED.get(peer.player.getUUID());
                helper.assertTrue(received != null && received.mainThread() && ((BiomeMessage) received.message()).biome() == holder,
                    "Dynamic holder did not resolve with the receiving player's registry");
            } finally { packet.getData().release(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void malformedUnknownAndWrongDirectionPacketsNeverInvokeHandlers(GameTestHelper helper) {
        for (int scenario = 0; scenario < 4; scenario++) {
            try (var peer = new Peer(helper)) {
                var data = new FriendlyByteBuf(Unpooled.buffer());
                data.writeByte(scenario == 3 ? 1 : 0);
                data.writeResourceLocation(scenario == 0 ? id("unknown") : ServerboundTimerFilterPacket.ID);
                if (scenario != 1) ServerboundTimerFilterPacket.STREAM_CODEC.encode(data, new ServerboundTimerFilterPacket(1, 20));
                if (scenario == 2) data.writeByte(99);
                var packet = new ServerboundCustomPayloadPacket(NAME, data);
                try {
                    try { NetworkHooks.onCustomPayload(packet, peer.connection); }
                    catch (IllegalStateException expected) {
                        helper.assertTrue(scenario == 3, "Unexpected native direction failure");
                    }
                    helper.assertTrue(!peer.connection.isConnected(), "Malformed packet did not disconnect");
                    helper.assertTrue(!RECEIVED.containsKey(peer.player.getUUID()), "Invalid packet invoked a handler");
                } finally { data.release(); }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeServerboundLimitAndUnregisteredClassesAreRejected(GameTestHelper helper) {
        boolean oversized = false;
        try { CHANNEL.toVanillaPacket(new LargeMessage(new byte[RegistryNetworkChannel.MAX_SERVERBOUND_BYTES]),
                NetworkDirection.PLAY_TO_SERVER, helper.getLevel().registryAccess()); }
        catch (EncoderException expected) { oversized = true; }
        helper.assertTrue(oversized, "Oversized payload escaped the native limit");
        boolean unregistered = false;
        try { CHANNEL.toVanillaPacket(new Object(), NetworkDirection.PLAY_TO_SERVER, helper.getLevel().registryAccess()); }
        catch (EncoderException expected) { unregistered = true; }
        helper.assertTrue(unregistered, "Unknown message class was encoded");
        helper.succeed();
    }
}
