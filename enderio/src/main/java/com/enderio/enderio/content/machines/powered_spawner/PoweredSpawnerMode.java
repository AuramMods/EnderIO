package com.enderio.enderio.content.machines.powered_spawner;

import com.enderio.core.common.lang.EnumLangMap;
import com.enderio.enderio.EnderIO;
import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.MutableComponent;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

import java.util.Objects;
import java.util.function.IntFunction;

public enum PoweredSpawnerMode implements StringRepresentable {
    SPAWN(0, "spawn"), CAPTURE(1, "capture");

    public static final Codec<PoweredSpawnerMode> CODEC = StringRepresentable.fromEnum(PoweredSpawnerMode::values);
    public static final IntFunction<PoweredSpawnerMode> BY_ID = ByIdMap.continuous(key -> key.id, values(),
            ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final PacketCodec<PoweredSpawnerMode> STREAM_CODEC = PacketCodecs.idMapper(BY_ID,
            v -> v.id);

    private static final EnumLangMap<PoweredSpawnerMode> LANG_MAP = new EnumLangMap<>(PoweredSpawnerMode.class, EnderIO.MOD_ID,
        "powered_spawner_mode");

    private final int id;
    private final String name;

    PoweredSpawnerMode(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Tag save(HolderLookup.Provider lookupProvider) {
        return CODEC.encodeStart(net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE, lookupProvider), this).getOrThrow(false, message -> { throw new IllegalArgumentException(message); });
    }

    public static PoweredSpawnerMode parse(HolderLookup.Provider lookupProvider, Tag tag) {
        return CODEC.parse(net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE, lookupProvider), tag).getOrThrow(false, message -> { throw new IllegalArgumentException(message); });
    }

    public MutableComponent getComponent() {
        return Objects.requireNonNull(LANG_MAP.get(this));
    }
}
