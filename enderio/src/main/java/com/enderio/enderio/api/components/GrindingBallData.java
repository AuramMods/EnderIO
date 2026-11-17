package com.enderio.enderio.api.components;

import com.enderio.core.common.datamap.RegistryDataMap;
import net.minecraft.resources.ResourceLocation;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;
import net.minecraft.world.item.Item;

import org.slf4j.Logger;

import java.util.Optional;

public record GrindingBallData(float outputMultiplier, float bonusMultiplier, float powerUse, int durability) {

    public static final Codec<GrindingBallData> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            Codec.FLOAT.fieldOf("OutputMultiplier").forGetter(GrindingBallData::outputMultiplier),
            Codec.FLOAT.fieldOf("BonusMultiplier").forGetter(GrindingBallData::bonusMultiplier),
            Codec.FLOAT.fieldOf("PowerUse").forGetter(GrindingBallData::powerUse),
            Codec.INT.fieldOf("Durability").forGetter(GrindingBallData::durability)
        ).apply(instance, GrindingBallData::new)
    );

    public static final PacketCodec<GrindingBallData> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.FLOAT,
        GrindingBallData::outputMultiplier,
        PacketCodecs.FLOAT,
        GrindingBallData::bonusMultiplier,
        PacketCodecs.FLOAT,
        GrindingBallData::powerUse,
        PacketCodecs.INT,
        GrindingBallData::durability,
        GrindingBallData::new
    );

    public static final RegistryDataMap<Item, GrindingBallData> DATA_MAP_TYPE = new RegistryDataMap<>(
        ResourceLocation.fromNamespaceAndPath("enderio", "grinding_ball"), Registries.ITEM, CODEC, false);

    public static final GrindingBallData IDENTITY = new GrindingBallData(1.0f, 1.0f, 1.0f, 0);

    public boolean isIdentity() {
        return this.equals(IDENTITY);
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    public Tag save(HolderLookup.Provider lookupProvider) {
        if (this.isIdentity()) {
            throw new IllegalStateException("Cannot encode identity GrindingBallData");
        } else {
            return CODEC.encodeStart(net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE, lookupProvider), this).getOrThrow(false, message -> {});
        }
    }

    public Tag saveOptional(HolderLookup.Provider lookupProvider) {
        return this.isIdentity() ? new CompoundTag() : save(lookupProvider);
    }

    public static Optional<GrindingBallData> parse(HolderLookup.Provider lookupProvider, Tag tag) {
        return CODEC.parse(net.minecraft.resources.RegistryOps.create(NbtOps.INSTANCE, lookupProvider), tag)
            .resultOrPartial(error -> LOGGER.error("Tried to load invalid GrindingBallData: '{}'", error));
    }

    public static GrindingBallData parseOptional(HolderLookup.Provider lookupProvider, CompoundTag tag) {
        return tag.isEmpty() ? IDENTITY : parse(lookupProvider, tag).orElse(IDENTITY);
    }
}
