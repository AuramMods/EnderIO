package com.enderio.enderio.foundation.io;

import com.enderio.core.common.item.ItemDataKey;
import com.enderio.enderio.api.EnderIOAPI;

import com.enderio.core.common.network.NetworkDataSlot;
import com.enderio.enderio.api.io.IOMode;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public record IOConfig(Map<Direction, IOMode> modes) {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Codec<IOConfig> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(Codec.unboundedMap(Direction.CODEC, IOMode.CODEC).fieldOf("Modes").forGetter(IOConfig::modes))
            .apply(instance, IOConfig::new));

    public static final ItemDataKey<IOConfig> ITEM_DATA = new ItemDataKey<>(EnderIOAPI.rl("io_config"), CODEC);

    public static final PacketCodec<IOConfig> STREAM_CODEC = PacketCodec.composite(
            PacketCodec.map(c -> new EnumMap<>(Direction.class), PacketCodecs.DIRECTION, IOMode.STREAM_CODEC),
            IOConfig::modes, IOConfig::new);

    public static final NetworkDataSlot.CodecType<IOConfig> DATA_SLOT_TYPE = new NetworkDataSlot.CodecType<>(CODEC,
            STREAM_CODEC);

    public static IOConfig copyOf(IOConfig other) {
        return new IOConfig(new EnumMap<>(other.modes()));
    }

    public static IOConfig empty() {
        return new IOConfig(new EnumMap<>(Direction.class));
    }

    public static IOConfig of(IOMode ioMode) {
        var config = new EnumMap<Direction, IOMode>(Direction.class);
        for (Direction d : Direction.values()) {
            config.put(d, ioMode);
        }

        return new IOConfig(config);
    }

    public static IOConfig of(Function<Direction, IOMode> mode) {
        var config = new EnumMap<Direction, IOMode>(Direction.class);
        for (Direction d : Direction.values()) {
            config.put(d, mode.apply(d));
        }

        return new IOConfig(config);
    }

    public IOMode getMode(Direction side) {
        return modes.getOrDefault(side, IOMode.NONE);
    }

    public IOConfig withMode(Direction side, IOMode mode) {
        Map<Direction, IOMode> newModes = new EnumMap<>(Direction.class);
        newModes.putAll(modes);
        newModes.put(side, mode);
        return new IOConfig(newModes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        IOConfig that = (IOConfig) o;
        return Objects.equals(modes, that.modes);
    }

    /**
     * Simplified hashcode impl for EnumMap using ordinal instead of hashcode for enums
     */
    @Override
    public int hashCode() {
        int h = 0;

        for (var entry : modes.entrySet()) {
            h += entry.getKey().ordinal() ^ (31 * entry.getValue().ordinal());
        }

        return h;
    }

    public Tag save() {
        return CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow(false, message -> { throw new IllegalArgumentException(message); });
    }

    public static Optional<IOConfig> parse(Tag tag) {
        if (tag instanceof CompoundTag compound && !compound.contains("Modes")) {
            // Ender IO 6.x: numeric direction names mapped to mode ordinal bytes.
            var modes = new EnumMap<Direction, IOMode>(Direction.class);
            for (String key : compound.getAllKeys()) {
                try {
                    int direction = Integer.parseInt(key);
                    int mode = compound.getByte(key);
                    if (!compound.contains(key, Tag.TAG_BYTE) || direction < 0 || direction >= 6 || mode < 0 || mode >= IOMode.values().length) {
                        return Optional.empty();
                    }
                    modes.put(Direction.from3DDataValue(direction), IOMode.BY_ID.apply(mode));
                } catch (NumberFormatException e) { return Optional.empty(); }
            }
            return Optional.of(new IOConfig(modes));
        }
        return CODEC.parse(NbtOps.INSTANCE, tag).resultOrPartial(error -> LOGGER.error("Tried to load invalid IOConfig: '{}'", error));
    }

    public static IOConfig parseOptional(CompoundTag tag) { return parse(tag).orElse(empty()); }

    public Tag save(HolderLookup.Provider registries) { return save(); }

    public static Optional<IOConfig> parse(HolderLookup.Provider registries, Tag tag) { return parse(tag); }

    public static IOConfig parseOptional(HolderLookup.Provider registries, CompoundTag tag) { return parseOptional(tag); }
}
