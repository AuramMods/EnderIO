package com.enderio.enderio.content.travel;

import com.enderio.enderio.api.travel.TravelTarget;
import com.enderio.enderio.content.travel.travel_anchor.AnchorTravelTarget;
import com.enderio.enderio.foundation.network.packets.ClientboundSyncTravelDataPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundTravelTargetRemovedPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundTravelTargetUpdatedPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.registries.ForgeRegistries;
import com.enderio.enderio.foundation.network.EIONetwork;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.Objects;
import net.minecraftforge.event.level.LevelEvent;
import java.util.stream.Stream;

@EventBusSubscriber(modid = "enderio", bus = EventBusSubscriber.Bus.FORGE)
public class TravelTargetSavedData extends SavedData {

    // Client worlds with the same dimension ID on different connections must not share targets.
    private static final Map<Level, TravelTargetSavedData> CLIENT_DATA = Collections.synchronizedMap(new WeakHashMap<>());
    private final HolderLookup.Provider registries;

    public static final String TARGETS = "targets";
    private final Map<BlockPos, TravelTarget> travelTargets = new HashMap<>();
    private ListTag untranslatedTargets = new ListTag();

    public TravelTargetSavedData(HolderLookup.Provider registries) {
        this.registries = Objects.requireNonNull(registries);
    }

    public TravelTargetSavedData(CompoundTag nbt, HolderLookup.Provider lookupProvider) {
        this(lookupProvider);
        this.loadNBT(lookupProvider, nbt);
    }

    public static TravelTargetSavedData getTravelData(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getDataStorage()
                    .computeIfAbsent(tag -> new TravelTargetSavedData(tag, level.registryAccess()),
                        () -> new TravelTargetSavedData(level.registryAccess()), "enderio_traveldata");
        } else {
            return CLIENT_DATA.computeIfAbsent(level, l -> new TravelTargetSavedData(l.registryAccess()));
        }
    }

    public Optional<TravelTarget> getTravelTarget(BlockPos pos) {
        return Optional.ofNullable(travelTargets.get(pos));
    }

    public Collection<TravelTarget> getTravelTargets() {
        return travelTargets.values();
    }

    public Stream<TravelTarget> getTravelTargetsInItemRange(BlockPos center) {
        return travelTargets.entrySet()
                .stream()
                .filter(entry -> entry.getValue().item2BlockRange() == Integer.MAX_VALUE
                        || center.distSqr(entry.getKey()) < (double) entry.getValue().item2BlockRange()
                                * entry.getValue().item2BlockRange())
                .map(Map.Entry::getValue);
    }

    // Adds or updates.
    public void setTravelTarget(Level level, TravelTarget target) {
        if (level instanceof ServerLevel serverLevel) {
            EIONetwork.sendToPlayersInDimension(serverLevel, new ClientboundTravelTargetUpdatedPacket(target));
        }

        travelTargets.put(target.pos(), target);
    }

    public void removeTravelTargetAt(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            EIONetwork.sendToPlayersInDimension(serverLevel, new ClientboundTravelTargetRemovedPacket(pos));
        }

        travelTargets.remove(pos);
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        var targets = serializeTargets();
        untranslatedTargets.forEach(target -> targets.add(target.copy()));
        nbt.put(TARGETS, targets);
        return nbt;
    }

    private ListTag serializeTargets() {
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        var targets = new ListTag();
        for (var target : travelTargets.values()) {
            targets.add(TravelTarget.CODEC.encodeStart(ops, target).getOrThrow(false, message -> {}));
        }
        return targets;
    }

    private void sendTravelData(ServerPlayer player) {
        var snapshot = new CompoundTag();
        snapshot.put(TARGETS, serializeTargets());
        EIONetwork.sendToPlayer(player, new ClientboundSyncTravelDataPacket(snapshot));
    }

    public void loadNBT(HolderLookup.Provider lookupProvider, CompoundTag nbt) {
        Map<BlockPos, TravelTarget> loaded = new HashMap<>();
        ListTag untranslated = new ListTag();
        var ops = RegistryOps.create(NbtOps.INSTANCE, lookupProvider);
        ListTag targets = nbt.getList(TARGETS, Tag.TAG_COMPOUND);
        for (var raw : targets) {
            var record = (CompoundTag) raw;
            if (!record.contains("type") && record.contains("name", Tag.TAG_STRING) && record.contains("data", Tag.TAG_COMPOUND)) {
                var target = loadForge6Anchor(record);
                if (target.isPresent()) {
                    loaded.putIfAbsent(target.get().pos(), target.get());
                } else {
                    // Preserve unsupported addon records and malformed old anchors through a save.
                    untranslated.add(record.copy());
                }
            } else {
                var target = TravelTarget.CODEC.parse(ops, record).getOrThrow(false, message -> {});
                loaded.put(target.pos(), target);
            }
        }
        travelTargets.clear();
        travelTargets.putAll(loaded);
        untranslatedTargets = untranslated;
    }

    private static Optional<AnchorTravelTarget> loadForge6Anchor(CompoundTag record) {
        if (!record.getString("name").equals("enderio:travel_anchor")) return Optional.empty();
        var data = record.getCompound("data");
        var pos = data.getCompound("BlockPos");
        if (!pos.contains("X", Tag.TAG_INT) || !pos.contains("Y", Tag.TAG_INT) || !pos.contains("Z", Tag.TAG_INT)) {
            return Optional.empty();
        }

        // Forge's saved registry aliases are applied before the dimension reads this file.
        var id = ResourceLocation.tryParse(data.getString("AnchorIcon"));
        var icon = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        return Optional.of(new AnchorTravelTarget(NbtUtils.readBlockPos(pos), data.getString("AnchorName"),
            icon == null ? Items.AIR : icon, data.getBoolean("AnchorVisibility")));
    }

    @Override
    public boolean isDirty() {
        return true;
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide()) CLIENT_DATA.remove(level);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            getTravelData(serverPlayer.level()).sendTravelData(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            getTravelData(serverPlayer.level()).sendTravelData(serverPlayer);
        }
    }
}
