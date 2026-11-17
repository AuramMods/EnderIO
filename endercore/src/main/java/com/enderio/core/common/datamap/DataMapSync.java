package com.enderio.core.common.datamap;

import com.enderio.core.EnderCore;
import com.enderio.core.common.network.RegistryNetworkChannel;
import com.enderio.core.common.network.codec.PacketCodec;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.network.NetworkDirection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Ordered bounded chunks, published atomically only after the complete snapshot decodes. */
public final class DataMapSync {
    public static final int CHUNK_BYTES = 128 * 1024;
    public static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final int MAX_VALUE_CHARS = 1_048_576;
    private static final Map<RecipeManager, Transfer> PENDING = new WeakHashMap<>();
    private static RegistryNetworkChannel channel;

    private DataMapSync() {}

    public static void registerNetwork() {
        channel = new RegistryNetworkChannel(EnderCore.loc("data_maps"), "forge-1");
        channel.register(EnderCore.loc("data_map_chunk"), Chunk.class, Chunk.CODEC, NetworkDirection.PLAY_TO_CLIENT,
            (chunk, player) -> {
                try { accept(player.level().getRecipeManager(), player.level().registryAccess(), chunk); }
                catch (RuntimeException error) {
                    com.mojang.logging.LogUtils.getLogger().error("Rejected data map synchronization", error);
                    ClientDataMaps.disconnectMalformed();
                }
            });
    }

    public static void send(ServerPlayer player) {
        for (var chunk : packets(DataMaps.snapshot(player.level().getRecipeManager()), player.level().registryAccess())) {
            channel.sendToPlayer(player, chunk);
        }
    }

    public static List<Chunk> packets(DataMaps.Snapshot snapshot, RegistryAccess registries) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer(256, MAX_BYTES));
        try {
            var types = DataMaps.types();
            buffer.writeVarInt(types.size());
            for (var type : types) writeMap(buffer, snapshot, type, registries);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            var id = UUID.randomUUID();
            var packets = new ArrayList<Chunk>();
            for (int offset = 0; offset < data.length; offset += CHUNK_BYTES) {
                packets.add(new Chunk(id, data.length, offset / CHUNK_BYTES,
                    Arrays.copyOfRange(data, offset, Math.min(data.length, offset + CHUNK_BYTES))));
            }
            return List.copyOf(packets);
        } finally { buffer.release(); }
    }

    private static <R, V> void writeMap(FriendlyByteBuf buffer, DataMaps.Snapshot snapshot,
                                       RegistryDataMap<R, V> type, RegistryAccess registries) {
        buffer.writeResourceLocation(type.id());
        buffer.writeResourceLocation(type.registryKey().location());
        var values = snapshot.values(type);
        buffer.writeVarInt(values.size());
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        values.forEach((id, value) -> {
            buffer.writeResourceLocation(id);
            buffer.writeUtf(type.codec().encodeStart(ops, value).getOrThrow(false, message -> {}).toString(), MAX_VALUE_CHARS);
        });
    }

    public static synchronized void accept(RecipeManager owner, RegistryAccess registries, Chunk chunk) {
        try {
            if (chunk.index() == 0) PENDING.put(owner, new Transfer(chunk.id(), chunk.totalBytes()));
            var transfer = PENDING.get(owner);
            if (transfer == null || !transfer.id.equals(chunk.id()) || transfer.data.length != chunk.totalBytes()
                || transfer.nextIndex != chunk.index()) throw new DecoderException("Out-of-order data map chunks");
            System.arraycopy(chunk.data, 0, transfer.data, chunk.index() * CHUNK_BYTES, chunk.data.length);
            transfer.nextIndex++;
            if (chunk.index() * CHUNK_BYTES + chunk.data.length == transfer.data.length) {
                PENDING.remove(owner);
                DataMaps.replace(owner, decode(transfer.data, registries));
            }
        } catch (RuntimeException error) {
            PENDING.remove(owner);
            throw error;
        }
    }

    private static DataMaps.Snapshot decode(byte[] bytes, RegistryAccess registries) {
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            var expected = new LinkedHashMap<ResourceLocation, RegistryDataMap<?, ?>>();
            DataMaps.types().forEach(type -> expected.put(type.id(), type));
            int count = buffer.readVarInt();
            if (count != expected.size()) throw new DecoderException("Mismatched mandatory data maps");
            var maps = new LinkedHashMap<RegistryDataMap<?, ?>, Map<ResourceLocation, ?>>();
            for (int i = 0; i < count; i++) {
                var type = expected.remove(buffer.readResourceLocation());
                if (type == null || !type.registryKey().location().equals(buffer.readResourceLocation())) {
                    throw new DecoderException("Unknown or duplicate data map");
                }
                maps.put(type, readMap(buffer, type, registries));
            }
            if (buffer.isReadable()) throw new DecoderException("Trailing data map snapshot bytes");
            return new DataMaps.Snapshot(maps);
        } finally { buffer.release(); }
    }

    private static <R, V> Map<ResourceLocation, V> readMap(FriendlyByteBuf buffer, RegistryDataMap<R, V> type,
                                                        RegistryAccess registries) {
        var registry = registries.registryOrThrow(type.registryKey());
        int size = buffer.readVarInt();
        if (size < 0 || size > registry.size()) throw new DecoderException("Invalid data map size");
        var values = new LinkedHashMap<ResourceLocation, V>();
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        for (int i = 0; i < size; i++) {
            var id = buffer.readResourceLocation();
            if (!registry.containsKey(id) || values.containsKey(id)) throw new DecoderException("Unknown or duplicate data map entry");
            var json = JsonParser.parseString(buffer.readUtf(MAX_VALUE_CHARS));
            values.put(id, type.codec().parse(ops, json).getOrThrow(false, message -> {}));
        }
        return values;
    }

    static synchronized void clear(RecipeManager owner) { PENDING.remove(owner); }

    public record Chunk(UUID id, int totalBytes, int index, byte[] data) {
        public static final PacketCodec<Chunk> CODEC = PacketCodec.of((buffer, value) -> {
            buffer.writeUUID(value.id);
            buffer.writeVarInt(value.totalBytes);
            buffer.writeVarInt(value.index);
            buffer.writeByteArray(value.data);
        }, buffer -> new Chunk(buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt(), buffer.readByteArray(CHUNK_BYTES)));

        public Chunk {
            if (totalBytes <= 0 || totalBytes > MAX_BYTES || index < 0 || index >= (totalBytes + CHUNK_BYTES - 1) / CHUNK_BYTES
                || data.length != Math.min(CHUNK_BYTES, totalBytes - index * CHUNK_BYTES)) {
                throw new DecoderException("Invalid data map chunk bounds");
            }
            data = data.clone();
        }

        @Override public byte[] data() { return data.clone(); }
    }

    private static final class Transfer {
        final UUID id;
        final byte[] data;
        int nextIndex;
        Transfer(UUID id, int length) { this.id = id; data = new byte[length]; }
    }
}
