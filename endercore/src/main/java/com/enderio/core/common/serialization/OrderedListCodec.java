package com.enderio.core.common.serialization;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Ensures the serialized list retains it's order.
 * WARNING: Editing this class *will* mess up saves.
 * Preserve the index/value representation, or provide an explicit legacy decoder.
 */
public class OrderedListCodec {

    public static <T> Codec<List<T>> create(Codec<T> itemCodec, T defaultValue) {
        return create(Integer.MAX_VALUE, itemCodec, defaultValue);
    }

    public static <T> Codec<List<T>> create(int maxSize, Codec<T> itemCodec, T defaultValue) {
        return createItemCodec(itemCodec, maxSize).listOf()
                .flatXmap(list -> fromItems(list, defaultValue, maxSize),
                    list -> checkSize(list, maxSize).map(OrderedListCodec::toItems));
    }

    private static <T> DataResult<List<T>> checkSize(List<T> values, int maxSize) {
        return values.size() <= maxSize ? DataResult.success(values)
            : DataResult.error(() -> "List has " + values.size() + " entries; maximum is " + maxSize);
    }

    private static <T> Codec<Item<T>> createItemCodec(Codec<T> itemCodec, int maxSize) {
        // @formatter:off
        return RecordCodecBuilder.create(inst -> inst
            .group(
                Codec.intRange(0, maxSize - 1).fieldOf("index").forGetter(Item::index),
                itemCodec.fieldOf("value").forGetter(Item::value)
            ).apply(inst, Item::new));
        // @formatter:on
    }

    private static <T> List<Item<T>> toItems(List<T> values) {
        List<Item<T>> items = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            items.add(new Item<>(i, values.get(i)));
        }

        return items;
    }

    private static <T> DataResult<List<T>> fromItems(List<Item<T>> slots, T defaultValue, int maxSize) {
        if (slots.size() > maxSize) {
            return DataResult.error(() -> "List has " + slots.size() + " entries; maximum is " + maxSize);
        }
        // DFU 6 can retain an out-of-range integer as a partial result. Never
        // use that partial index to allocate or address the restored list.
        for (Item<T> slot : slots) {
            if (slot.index() < 0 || slot.index() >= maxSize) {
                return DataResult.error(() -> "Slot index out of range: " + slot.index());
            }
        }
        OptionalInt optionalint = slots.stream().mapToInt(Item::index).max();
        if (optionalint.isEmpty()) {
            return DataResult.success(List.of());
        }

        List<T> fluids = NonNullList.withSize(optionalint.getAsInt() + 1, defaultValue);
        for (Item<T> slot : slots) {
            fluids.set(slot.index, slot.value);
        }
        return DataResult.success(fluids);
    }

    private record Item<T>(int index, T value) {
    }
}
