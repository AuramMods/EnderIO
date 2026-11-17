package com.enderio.enderio.content.conduits.legacy;

import com.enderio.enderio.api.conduits.network.node.legacy.ConduitData;
import com.enderio.enderio.api.conduits.network.node.legacy.ConduitDataAccessor;
import com.enderio.enderio.api.conduits.network.node.legacy.ConduitDataType;
import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * A safe way to store conduit data.
 */
@Deprecated(since = "8.0.0")
public class ConduitDataContainer implements ConduitDataAccessor {

    public static final Codec<ConduitDataContainer> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<ConduitDataContainer, T>> decode(DynamicOps<T> ops, T input) {
            boolean empty = ops.getMap(input).result().map(map -> map.entries().findAny().isEmpty()).orElse(false);
            return empty ? DataResult.success(Pair.of(new ConduitDataContainer(), ops.empty()))
                : ConduitData.CODEC.decode(ops, input).map(pair -> pair.mapFirst(ConduitDataContainer::new));
        }

        @Override
        public <T> DataResult<T> encode(ConduitDataContainer container, DynamicOps<T> ops, T prefix) {
            return container.data == null ? ops.mergeToMap(prefix, java.util.Map.of())
                : ConduitData.CODEC.encode(container.data, ops, prefix);
        }
    };

    @Nullable
    private ConduitData<?> data;

    public ConduitDataContainer() {
    }

    public ConduitDataContainer(@Nullable ConduitData<?> data) {
        this.data = data;
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private ConduitDataContainer(Optional<ConduitData<?>> data) {
        this.data = data.orElse(null);
    }

    public boolean hasData() {
        return data != null;
    }

    public boolean hasData(ConduitDataType<?> type) {
        return data != null && data.type() == type;
    }

    @Nullable
    public ConduitData<?> getData() {
        return data;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T extends ConduitData<T>> T getData(ConduitDataType<T> type) {
        if (data != null && type == data.type()) {
            return (T) data;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public <T extends ConduitData<T>> T getOrCreateData(ConduitDataType<T> type) {
        if (data != null && type == data.type()) {
            return (T) data;
        }

        data = type.factory().get();
        return (T) data;
    }

    public Tag save(HolderLookup.Provider lookupProvider) {
        return CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, lookupProvider), this).getOrThrow(true, message -> {});
    }

    public static ConduitDataContainer parse(HolderLookup.Provider lookupProvider, Tag tag) {
        return CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, lookupProvider), tag).getOrThrow(true, message -> {});
    }

    public ConduitDataContainer deepCopy() {
        return new ConduitDataContainer(data == null ? null : data.deepCopy());
    }

    @Override
    public int hashCode() {
        return Objects.hash(data);
    }
}
