package com.enderio.core.common.network;

import com.enderio.core.EnderCore;
import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.network.menu.ClientboundSyncSlotDataPacket;
import com.enderio.core.common.network.menu.ServerboundSetSyncSlotDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;

public final class CoreNetwork {
    private static final String PROTOCOL_VERSION = "forge-2";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(EnderCore.loc("main"),
        () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);

    private CoreNetwork() {}

    public static void register() {
        client(0, EmitParticlePacket.class, EmitParticlePacket.STREAM_CODEC,
            (packet, context) -> ClientPayloadHandler.getInstance().handleEmitParticle(packet, context));
        client(1, EmitParticlesPacket.class, EmitParticlesPacket.STREAM_CODEC,
            (packet, context) -> ClientPayloadHandler.getInstance().handleEmitParticles(packet, context));
        client(2, ServerboundCDataSlotUpdate.class, ServerboundCDataSlotUpdate.STREAM_CODEC,
            (packet, context) -> ClientPayloadHandler.getInstance().handleDataSlotUpdate(packet, context));
        register(3, ClientboundDataSlotChange.class, ClientboundDataSlotChange.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, ServerPayloadHandler.getInstance()::handleDataSlotChange);
        client(4, ClientboundSyncSlotDataPacket.class, ClientboundSyncSlotDataPacket.STREAM_CODEC,
            (packet, context) -> ClientPayloadHandler.getInstance().handleSyncSlotDataPacket(packet, context));
        register(5, ServerboundSetSyncSlotDataPacket.class, ServerboundSetSyncSlotDataPacket.STREAM_CODEC,
            NetworkDirection.PLAY_TO_SERVER, ServerPayloadHandler.getInstance()::handleSetSyncSlotDataPacket);
    }

    private static <T> void client(int id, Class<T> type, PacketCodec<T> codec, BiConsumer<T, NetworkEvent.Context> handler) {
        register(id, type, codec, NetworkDirection.PLAY_TO_CLIENT, (packet, context) ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handler.accept(packet, context)));
    }

    private static <T> void register(int id, Class<T> type, PacketCodec<T> codec, NetworkDirection direction,
            BiConsumer<T, NetworkEvent.Context> handler) {
        CHANNEL.messageBuilder(type, id, direction)
            .encoder((packet, buffer) -> codec.encode(buffer, packet))
            .decoder(codec::decode)
            .consumerMainThread((packet, context) -> handler.accept(packet, context.get()))
            .add();
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToPlayersTrackingChunk(ServerLevel level, ChunkPos pos, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunk(pos.x, pos.z)), packet);
    }
}
