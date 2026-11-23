package com.enderio.core.common.datamap;

import com.enderio.core.EnderCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.UnaryOperator;

/** Each loaded resource set / client connection owns an independent immutable snapshot. */
@Mod.EventBusSubscriber(modid = EnderCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DataMaps {
    private static final Map<net.minecraft.resources.ResourceLocation, RegistryDataMap<?, ?>> TYPES = new LinkedHashMap<>();
    private static final Map<RecipeManager, Snapshot> SNAPSHOTS = new WeakHashMap<>();
    private static final Snapshot EMPTY = new Snapshot(Map.of());

    private DataMaps() {}

    public static synchronized void register(RegistryDataMap<?, ?> type) {
        if (TYPES.putIfAbsent(type.id(), type) != null) throw new IllegalArgumentException("Duplicate data map: " + type.id());
    }

    public static synchronized List<RegistryDataMap<?, ?>> types() {
        return List.copyOf(TYPES.values());
    }

    public static synchronized Snapshot snapshot(RecipeManager owner) {
        return SNAPSHOTS.getOrDefault(owner, EMPTY);
    }

    public static synchronized void replace(RecipeManager owner, Snapshot snapshot) {
        SNAPSHOTS.put(owner, snapshot);
    }

    /** Edits the active reload's data snapshot. Returning null removes the entry. */
    public static synchronized <R, V> void edit(RecipeManager owner, RegistryDataMap<R, V> type,
            ResourceLocation id, UnaryOperator<V> change) {
        var current = snapshot(owner);
        var values = new LinkedHashMap<>(current.values(type));
        var next = change.apply(values.get(id));
        if (next == null) {
            values.remove(id);
        } else {
            values.put(id, next);
        }
        var maps = new LinkedHashMap<>(current.maps());
        maps.put(type, values);
        replace(owner, new Snapshot(maps));
    }

    public static void clear(RecipeManager owner) {
        synchronized (DataMaps.class) { SNAPSHOTS.remove(owner); }
        DataMapSync.clear(owner);
    }

    static <R, V> Map<net.minecraft.resources.ResourceLocation, V> values(RecipeManager owner, RegistryDataMap<R, V> type) {
        return snapshot(owner).values(type);
    }

    public record Snapshot(Map<RegistryDataMap<?, ?>, Map<net.minecraft.resources.ResourceLocation, ?>> maps) {
        public Snapshot {
            var copy = new LinkedHashMap<RegistryDataMap<?, ?>, Map<net.minecraft.resources.ResourceLocation, ?>>();
            maps.forEach((type, values) -> copy.put(type, Map.copyOf(values)));
            maps = Map.copyOf(copy);
        }

        @SuppressWarnings("unchecked")
        public <R, V> Map<net.minecraft.resources.ResourceLocation, V> values(RegistryDataMap<R, V> type) {
            return (Map<net.minecraft.resources.ResourceLocation, V>) maps.getOrDefault(type, Map.of());
        }
    }

    @SubscribeEvent
    public static void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new DataMapReloadListener(event.getServerResources().getRecipeManager(),
            event.getRegistryAccess(), event.getConditionContext(), types()));
    }

    @SubscribeEvent
    public static void sync(OnDatapackSyncEvent event) {
        var players = event.getPlayer() == null ? event.getPlayerList().getPlayers() : List.of(event.getPlayer());
        for (ServerPlayer player : players) DataMapSync.send(player);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        clear(event.getServer().getRecipeManager());
    }
}
