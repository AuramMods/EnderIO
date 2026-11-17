package com.enderio.core.common.datamap;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** Immutable values associated with registry IDs by reloadable server data. */
public record RegistryDataMap<R, V>(ResourceLocation id, ResourceKey<Registry<R>> registryKey,
                                    Codec<V> codec, boolean advancedRemovals) {
    public ResourceLocation file() {
        var registry = registryKey.location();
        String folder = (registry.getNamespace().equals("minecraft") ? "" : registry.getNamespace() + "/") + registry.getPath();
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "data_maps/" + folder + "/" + id.getPath() + ".json");
    }

    public @Nullable V get(@Nullable Level level, R object) {
        if (level == null) return null;
        return level.registryAccess().registryOrThrow(registryKey).getResourceKey(object)
            .map(key -> values(level.getRecipeManager()).get(key.location())).orElse(null);
    }

    public Map<ResourceLocation, V> values(RecipeManager owner) {
        return DataMaps.values(owner, this);
    }
}
