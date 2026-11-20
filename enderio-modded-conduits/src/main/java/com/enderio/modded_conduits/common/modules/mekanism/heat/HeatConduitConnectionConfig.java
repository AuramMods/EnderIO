package com.enderio.modded_conduits.common.modules.mekanism.heat;

import com.enderio.enderio.api.conduits.ConduitRedstoneSignalAware;
import com.enderio.enderio.api.conduits.connection.config.ConnectionConfig;
import com.enderio.enderio.api.conduits.connection.config.ConnectionConfigType;
import com.enderio.enderio.api.conduits.connection.config.IOConnectionConfig;
import com.enderio.enderio.api.conduits.connection.config.RedstoneSensitiveConnectionConfig;
import com.enderio.enderio.api.io.RedstoneControl;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.world.item.DyeColor;

import java.util.List;

public record HeatConduitConnectionConfig(boolean isInsert, boolean isExtract, RedstoneControl extractRedstoneControl,
        DyeColor extractRedstoneChannel) implements IOConnectionConfig, RedstoneSensitiveConnectionConfig {

    public static final HeatConduitConnectionConfig DEFAULT = new HeatConduitConnectionConfig(true, true,
            RedstoneControl.ALWAYS_ACTIVE, DyeColor.RED);

    public static final MapCodec<HeatConduitConnectionConfig> CODEC = RecordCodecBuilder.mapCodec(inst -> inst
            .group(Codec.BOOL.fieldOf("is_insert").forGetter(HeatConduitConnectionConfig::isInsert),
                    Codec.BOOL.fieldOf("is_extract").forGetter(HeatConduitConnectionConfig::isExtract),
                    RedstoneControl.CODEC.fieldOf("extract_redstone_control")
                            .forGetter(HeatConduitConnectionConfig::extractRedstoneControl),
                    DyeColor.CODEC.fieldOf("extract_redstone_channel")
                            .forGetter(HeatConduitConnectionConfig::extractRedstoneChannel))
            .apply(inst, HeatConduitConnectionConfig::new));

    // @formatter:off
    public static final PacketCodec<HeatConduitConnectionConfig> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.BOOL,
        HeatConduitConnectionConfig::isInsert,
        PacketCodecs.BOOL,
        HeatConduitConnectionConfig::isExtract,
        RedstoneControl.STREAM_CODEC,
        HeatConduitConnectionConfig::extractRedstoneControl,
        PacketCodecs.DYE_COLOR,
        HeatConduitConnectionConfig::extractRedstoneChannel,
        HeatConduitConnectionConfig::new);
    // @formatter:on

    public static final ConnectionConfigType<HeatConduitConnectionConfig> TYPE = new ConnectionConfigType<>(CODEC,
            STREAM_CODEC, () -> DEFAULT);

    @Override
    public DyeColor insertChannel() {
        return DyeColor.RED;
    }

    @Override
    public DyeColor extractChannel() {
        return DyeColor.RED;
    }

    @Override
    public ConnectionConfig reconnected() {
        return new HeatConduitConnectionConfig(DEFAULT.isInsert, DEFAULT.isExtract, extractRedstoneControl, extractRedstoneChannel);
    }

    @Override
    public ConnectionConfig disconnected() {
        return null;
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

    public HeatConduitConnectionConfig withIsInsert(boolean isInsert) {
        return new HeatConduitConnectionConfig(isInsert, isExtract, extractRedstoneControl, extractRedstoneChannel);
    }

    public HeatConduitConnectionConfig withIsExtract(boolean isExtract) {
        return new HeatConduitConnectionConfig(isInsert, isExtract, extractRedstoneControl, extractRedstoneChannel);
    }

    public HeatConduitConnectionConfig withExtractRedstoneControl(RedstoneControl extractRedstoneControl) {
        return new HeatConduitConnectionConfig(isInsert, isExtract, extractRedstoneControl, extractRedstoneChannel);
    }

    public HeatConduitConnectionConfig withExtractRedstoneChannel(DyeColor extractRedstoneChannel) {
        return new HeatConduitConnectionConfig(isInsert, isExtract, extractRedstoneControl, extractRedstoneChannel);
    }

    @Override
    public ConnectionConfigType<?> type() {
        return TYPE;
    }
}
