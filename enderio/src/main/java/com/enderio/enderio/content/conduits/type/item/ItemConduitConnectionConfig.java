package com.enderio.enderio.content.conduits.type.item;

import com.enderio.enderio.api.conduits.ConduitRedstoneSignalAware;
import com.enderio.enderio.api.conduits.connection.config.ConnectionConfig;
import com.enderio.enderio.api.conduits.connection.config.ConnectionConfigType;
import com.enderio.enderio.api.conduits.connection.config.IOConnectionConfig;
import com.enderio.enderio.api.conduits.connection.config.RedstoneSensitiveConnectionConfig;
import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.api.network.MassiveStreamCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.world.item.DyeColor;

import java.util.List;

public record ItemConduitConnectionConfig(boolean isInsert, DyeColor insertChannel, boolean isExtract,
        DyeColor extractChannel, RedstoneControl extractRedstoneControl, DyeColor extractRedstoneChannel,
        boolean isRoundRobin, boolean isSelfFeed, int priority)
        implements IOConnectionConfig, RedstoneSensitiveConnectionConfig {

    public static final ItemConduitConnectionConfig DEFAULT = new ItemConduitConnectionConfig(false, DyeColor.GREEN, true,
            DyeColor.GREEN, RedstoneControl.NEVER_ACTIVE, DyeColor.RED, false, false, 0);

    public static final MapCodec<ItemConduitConnectionConfig> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Codec.BOOL.fieldOf("is_insert").forGetter(ItemConduitConnectionConfig::isInsert),
                    DyeColor.CODEC.fieldOf("insert_channel").forGetter(ItemConduitConnectionConfig::insertChannel),
                    Codec.BOOL.fieldOf("is_extract").forGetter(ItemConduitConnectionConfig::isExtract),
                    DyeColor.CODEC.fieldOf("extract_channel").forGetter(ItemConduitConnectionConfig::extractChannel),
                    RedstoneControl.CODEC.fieldOf("extract_redstone_control")
                            .forGetter(ItemConduitConnectionConfig::extractRedstoneControl),
                    DyeColor.CODEC.fieldOf("extract_redstone_channel")
                            .forGetter(ItemConduitConnectionConfig::extractRedstoneChannel),
                    Codec.BOOL.fieldOf("is_round_robin").forGetter(ItemConduitConnectionConfig::isRoundRobin),
                    Codec.BOOL.fieldOf("is_self_feed").forGetter(ItemConduitConnectionConfig::isSelfFeed),
                    Codec.INT.fieldOf("priority").forGetter(ItemConduitConnectionConfig::priority))
            .apply(instance, ItemConduitConnectionConfig::new));

    public static final PacketCodec<ItemConduitConnectionConfig> STREAM_CODEC = MassiveStreamCodec.composite(
            PacketCodecs.BOOL, ItemConduitConnectionConfig::isInsert, PacketCodecs.DYE_COLOR,
            ItemConduitConnectionConfig::insertChannel, PacketCodecs.BOOL, ItemConduitConnectionConfig::isExtract,
            PacketCodecs.DYE_COLOR, ItemConduitConnectionConfig::extractChannel, RedstoneControl.STREAM_CODEC,
            ItemConduitConnectionConfig::extractRedstoneControl, PacketCodecs.DYE_COLOR,
            ItemConduitConnectionConfig::extractRedstoneChannel, PacketCodecs.BOOL,
            ItemConduitConnectionConfig::isRoundRobin, PacketCodecs.BOOL, ItemConduitConnectionConfig::isSelfFeed,
            PacketCodecs.INT, ItemConduitConnectionConfig::priority, ItemConduitConnectionConfig::new);

    public static final ConnectionConfigType<ItemConduitConnectionConfig> TYPE = new ConnectionConfigType<>(CODEC,
            STREAM_CODEC, () -> DEFAULT);

    @Override
    public ConnectionConfig reconnected() {
        return new ItemConduitConnectionConfig(false, insertChannel, true, extractChannel, extractRedstoneControl,
                extractRedstoneChannel, isRoundRobin, isSelfFeed, priority);
    }

    @Override
    public ConnectionConfig disconnected() {
        return new ItemConduitConnectionConfig(false, insertChannel, false, extractChannel, extractRedstoneControl,
                extractRedstoneChannel, isRoundRobin, isSelfFeed, priority);
    }

    @Override
    public boolean canInsert(ConduitRedstoneSignalAware signalAware) {
        // TODO: sendRedstoneControl
        return isInsert();
    }

    @Override
    public boolean canExtract(ConduitRedstoneSignalAware signalAware) {
        return isExtract() && signalAware.isRedstoneControlActive(extractRedstoneControl, extractRedstoneChannel);
    }

    @Override
    public List<DyeColor> getRedstoneSignalColors() {
        if (extractRedstoneControl.isRedstoneSensitive()) {
            return List.of(extractRedstoneChannel);
        }

        return List.of();
    }

    public ItemConduitConnectionConfig withIsInsert(boolean isInsert) {
        return new ItemConduitConnectionConfig(isInsert, insertChannel, isExtract, extractChannel,
                extractRedstoneControl, extractRedstoneChannel, isRoundRobin, isSelfFeed, priority);
    }

    public ItemConduitConnectionConfig withInsertChannel(DyeColor insertChannel) {
        return new ItemConduitConnectionConfig(isInsert, insertChannel, isExtract, extractChannel,
                extractRedstoneControl, extractRedstoneChannel, isRoundRobin, isSelfFeed, priority);
    }

    public ItemConduitConnectionConfig withIsExtract(boolean isExtract) {
        return new ItemConduitConnectionConfig(isInsert, insertChannel, isExtract, extractChannel,
                extractRedstoneControl, extractRedstoneChannel, isRoundRobin, isSelfFeed, priority);
    }

    public ItemConduitConnectionConfig withExtractChannel(DyeColor extractChannel) {
        return new ItemConduitConnectionConfig(isInsert, insertChannel, isExtract, extractChannel,
                extractRedstoneControl, extractRedstoneChannel, isRoundRobin, isSelfFeed, priority);
    }

    public ItemConduitConnectionConfig withExtractRedstoneControl(RedstoneControl extractRedstoneControl) {
        return new ItemConduitConnectionConfig(isInsert, insertChannel, isExtract, extractChannel,
                extractRedstoneControl, extractRedstoneChannel, isRoundRobin, isSelfFeed, priority);
    }

    public ItemConduitConnectionConfig withExtractRedstoneChannel(DyeColor extractRedstoneChannel) {
        return new ItemConduitConnectionConfig(isInsert, insertChannel, isExtract, extractChannel,
                extractRedstoneControl, extractRedstoneChannel, isRoundRobin, isSelfFeed, priority);
    }

    public ItemConduitConnectionConfig withIsRoundRobin(boolean isRoundRobin) {
        return new ItemConduitConnectionConfig(isInsert, insertChannel, isExtract, extractChannel,
                extractRedstoneControl, extractRedstoneChannel, isRoundRobin, isSelfFeed, priority);
    }

    public ItemConduitConnectionConfig withIsSelfFeed(boolean isSelfFeed) {
        return new ItemConduitConnectionConfig(isInsert, insertChannel, isExtract, extractChannel,
                extractRedstoneControl, extractRedstoneChannel, isRoundRobin, isSelfFeed, priority);
    }

    public ItemConduitConnectionConfig withPriority(int priority) {
        return new ItemConduitConnectionConfig(isInsert, insertChannel, isExtract, extractChannel,
                extractRedstoneControl, extractRedstoneChannel, isRoundRobin, isSelfFeed,
                Math.min(9999, Math.max(-9999, priority)));
    }

    @Override
    public ConnectionConfigType<ItemConduitConnectionConfig> type() {
        return TYPE;
    }
}
