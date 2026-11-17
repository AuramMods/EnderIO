package com.enderio.enderio.api.capacitor;

import com.mojang.serialization.Codec;
import com.enderio.core.common.item.ItemDataKey;
import com.enderio.enderio.api.EnderIOAPI;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.network.codec.PacketCodec;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// TODO: Instead of loot capacitors having lists of specialized machines, have different loot capacitor items for different
//       machine categories.
// TODO: Loot capacitor types (Sculk, Soul) found in respective dungeons/structures.
// TODO: End game capacitor fabrication with mob fighting? Souls? Capacitor sacrifice?

public record CapacitorData(float base, Map<CapacitorModifier, Float> modifiers) {

    public static final Codec<Map<CapacitorModifier, Float>> MODIFIER_CODEC = Codec.unboundedMap(CapacitorModifier.CODEC, Codec.FLOAT);

    public static final Codec<CapacitorData> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            Codec.FLOAT.fieldOf("base").forGetter(CapacitorData::base),
            MODIFIER_CODEC.fieldOf("specializations").forGetter(CapacitorData::modifiers)
        ).apply(instance, CapacitorData::new)
    );

    public static final PacketCodec<Map<CapacitorModifier, Float>> MODIFIER_STREAM_CODEC
        = PacketCodec.map(HashMap::new, CapacitorModifier.STREAM_CODEC, PacketCodecs.FLOAT);

    public static final PacketCodec<CapacitorData> STREAM_CODEC = PacketCodec.composite(
        PacketCodecs.FLOAT,
        CapacitorData::base,
        MODIFIER_STREAM_CODEC,
        CapacitorData::modifiers,
        CapacitorData::new
    );

    public static final ItemDataKey<CapacitorData> ITEM_DATA = new ItemDataKey<>(EnderIOAPI.rl("capacitor_data"), CODEC);

    public static final CapacitorData NONE = simple(0.0f);

    public static CapacitorData simple(float base) {
        return new CapacitorData(base, Map.of());
    }

    public float getModifier(CapacitorModifier modifier) {
        if (modifiers.containsKey(modifier)) {
            return modifiers.get(modifier);
        }

        return base;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CapacitorData that = (CapacitorData) o;
        return Float.compare(base, that.base) == 0 && Objects.equals(modifiers, that.modifiers);
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(base);
        result = 31 * result + Objects.hashCode(modifiers);
        return result;
    }
}
