package com.enderio.core.common.datamap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Loads all resource-pack layers; tag resolution happens after the preparation barrier. */
public final class DataMapReloadListener extends SimplePreparableReloadListener<Map<RegistryDataMap<?, ?>, List<JsonObject>>> {
    private final RecipeManager owner;
    private final RegistryAccess registries;
    private final ICondition.IContext conditions;
    private final List<RegistryDataMap<?, ?>> types;

    public DataMapReloadListener(RecipeManager owner, RegistryAccess registries, ICondition.IContext conditions,
                                List<RegistryDataMap<?, ?>> types) {
        this.owner = owner;
        this.registries = registries;
        this.conditions = conditions;
        this.types = List.copyOf(types);
    }

    @Override
    protected Map<RegistryDataMap<?, ?>, List<JsonObject>> prepare(ResourceManager resources, ProfilerFiller profiler) {
        var result = new LinkedHashMap<RegistryDataMap<?, ?>, List<JsonObject>>();
        for (var type : types) {
            var layers = new ArrayList<JsonObject>();
            for (var resource : resources.getResourceStack(type.file())) {
                try (Reader reader = resource.openAsReader()) {
                    layers.add(GsonHelper.convertToJsonObject(JsonParser.parseReader(reader), "data map"));
                } catch (Exception error) {
                    LogUtils.getLogger().error("Cannot read data map {} from {}", type.file(), resource.sourcePackId(), error);
                }
            }
            result.put(type, layers);
        }
        return result;
    }

    @Override
    protected void apply(Map<RegistryDataMap<?, ?>, List<JsonObject>> input, ResourceManager resources, ProfilerFiller profiler) {
        var maps = new LinkedHashMap<RegistryDataMap<?, ?>, Map<ResourceLocation, ?>>();
        input.forEach((type, layers) -> maps.put(type, build(type, layers)));
        DataMaps.replace(owner, new DataMaps.Snapshot(maps));
    }

    private <R, V> Map<ResourceLocation, V> build(RegistryDataMap<R, V> type, List<JsonObject> layers) {
        var result = new LinkedHashMap<ResourceLocation, V>();
        var registry = registries.registryOrThrow(type.registryKey());
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        for (var layer : layers) {
            try {
                if (!matchesConditions(layer)) continue;
                boolean replace = replace(layer);
                var additions = new LinkedHashMap<Selector, V>();
                var values = GsonHelper.getAsJsonObject(layer, "values", new JsonObject());
                for (var entry : values.entrySet()) {
                    var selector = Selector.parse(entry.getKey());
                    var json = entry.getValue();
                    if (json.isJsonObject()) {
                        var object = json.getAsJsonObject().deepCopy();
                        if (!matchesConditions(object)) continue;
                        boolean conditional = object.has("neoforge:conditions") || object.has("forge:conditions") || object.has("conditions");
                        object.remove("neoforge:conditions");
                        object.remove("forge:conditions");
                        object.remove("conditions");
                        json = conditional && object.has("value") ? object.get("value") : object;
                    }
                    if (json.isJsonObject() && json.getAsJsonObject().has("value")) {
                        var object = json.getAsJsonObject();
                        replace(object);
                        json = object.get("value");
                    }
                    V value = type.codec().parse(ops, json).getOrThrow(false, message -> {});
                    additions.put(selector, value);
                }
                var removals = removals(layer.get("remove"), type.advancedRemovals());
                // Decode the complete layer before changing anything: a malformed value
                // must not apply its replace flag or leave a partially loaded file.
                if (replace) result.clear();
                additions.forEach((selector, value) -> resolve(type, registry, selector, true, id -> result.put(id, value)));
                removals.forEach(selector -> resolve(type, registry, selector, false, result::remove));
            } catch (RuntimeException error) {
                LogUtils.getLogger().error("Cannot decode data map layer {}", type.file(), error);
            }
        }
        return Map.copyOf(result);
    }

    private <R> void resolve(RegistryDataMap<R, ?> type, Registry<R> registry, Selector selector, boolean required,
                             Consumer<ResourceLocation> consumer) {
        if (selector.tag()) {
            for (Holder<R> holder : conditions.getTag(TagKey.create(type.registryKey(), selector.id()))) {
                holder.unwrapKey().ifPresent(key -> consumer.accept(key.location()));
            }
        } else if (registry.containsKey(selector.id())) {
            consumer.accept(selector.id());
        } else if (required) {
            LogUtils.getLogger().error("Unknown object {} in data map {} for {}", selector.id(), type.id(), type.registryKey().location());
        }
    }

    private static List<Selector> removals(JsonElement json, boolean advanced) {
        var result = new ArrayList<Selector>();
        if (json == null || json.isJsonNull()) return result;
        if (json.isJsonArray()) {
            Boolean objects = null;
            for (var value : json.getAsJsonArray()) {
                boolean object = value.isJsonObject();
                if (objects != null && objects != object) throw new IllegalArgumentException("Mixed data map removal formats");
                objects = object;
                if (advanced && object) value = value.getAsJsonObject().get("key");
                result.add(Selector.parse(Codec.STRING.parse(JsonOps.INSTANCE, value).getOrThrow(false, message -> {})));
            }
        } else if (advanced && json.isJsonObject()) {
            // These maps use the source default remover, which always removes the whole value.
            json.getAsJsonObject().keySet().forEach(key -> result.add(Selector.parse(key)));
        } else throw new IllegalArgumentException("Invalid data map removal list");
        return result;
    }

    private static boolean replace(JsonObject object) {
        var value = object.get("replace");
        return value != null && !value.isJsonNull() && Codec.BOOL.parse(JsonOps.INSTANCE, value).getOrThrow(false, message -> {});
    }

    private boolean matchesConditions(JsonObject object) {
        for (String key : List.of("forge:conditions", "conditions", "neoforge:conditions")) {
            if (object.has(key)) {
                var translated = object.deepCopy();
                translateConditions(translated.get(key));
                if (!CraftingHelper.processConditions(translated, key, conditions)) return false;
            }
        }
        return true;
    }

    private static void translateConditions(JsonElement json) {
        if (json.isJsonArray()) json.getAsJsonArray().forEach(DataMapReloadListener::translateConditions);
        else if (json.isJsonObject()) {
            var object = json.getAsJsonObject();
            if (object.has("type") && object.get("type").isJsonPrimitive()) {
                String type = object.get("type").getAsString();
                if (type.startsWith("neoforge:")) object.addProperty("type", "forge:" + type.substring(9));
            }
            object.entrySet().forEach(entry -> translateConditions(entry.getValue()));
        }
    }

    private record Selector(ResourceLocation id, boolean tag) {
        static Selector parse(String value) {
            boolean tag = value.startsWith("#");
            return new Selector(ResourceLocation.parse(tag ? value.substring(1) : value), tag);
        }
    }
}
