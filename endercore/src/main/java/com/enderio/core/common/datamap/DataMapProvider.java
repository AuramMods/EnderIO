package com.enderio.core.common.datamap;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Writes the same data_maps paths and value wrappers consumed by the runtime loader. */
public abstract class DataMapProvider implements DataProvider {
    private final PackOutput output;
    private final CompletableFuture<HolderLookup.Provider> lookup;
    private final Map<RegistryDataMap<?, ?>, Builder<?, ?>> builders = new LinkedHashMap<>();

    protected DataMapProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        this.output = output;
        this.lookup = lookup;
    }

    protected abstract void gather();

    @SuppressWarnings("unchecked")
    protected <R, V> Builder<R, V> builder(RegistryDataMap<R, V> type) {
        return (Builder<R, V>) builders.computeIfAbsent(type, ignored -> new Builder<>(type));
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        builders.clear();
        gather();
        return lookup.thenCompose(registries -> CompletableFuture.allOf(builders.values().stream().map(builder -> {
            var file = builder.type.file();
            var path = output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(file.getNamespace()).resolve(file.getPath());
            return DataProvider.saveStable(cache, builder.json(registries), path);
        }).toArray(CompletableFuture[]::new)));
    }

    @Override public String getName() { return "Ender IO data maps"; }

    public static final class Builder<R, V> {
        private final RegistryDataMap<R, V> type;
        private final Map<ResourceLocation, Entry<V>> values = new LinkedHashMap<>();
        private Builder(RegistryDataMap<R, V> type) { this.type = type; }

        public Builder<R, V> add(Holder<R> holder, V value, boolean replace) {
            return add(holder.unwrapKey().orElseThrow().location(), value, replace);
        }

        public Builder<R, V> add(ResourceLocation id, V value, boolean replace) {
            values.put(id, new Entry<>(value, replace));
            return this;
        }

        private JsonObject json(HolderLookup.Provider registries) {
            var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            var entries = new JsonObject();
            values.forEach((id, entry) -> {
                var json = type.codec().encodeStart(ops, entry.value()).getOrThrow(false, message -> {});
                if (entry.replace()) {
                    var wrapper = new JsonObject();
                    wrapper.addProperty("replace", true);
                    wrapper.add("value", json);
                    json = wrapper;
                }
                entries.add(id.toString(), json);
            });
            var root = new JsonObject();
            root.add("values", entries);
            return root;
        }
        private record Entry<V>(V value, boolean replace) {}
    }
}
