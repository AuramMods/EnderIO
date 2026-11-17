package com.enderio.enderio.content.travel;

import com.enderio.enderio.api.travel.TravelTarget;
import com.enderio.enderio.foundation.network.packets.ClientboundSyncTravelDataPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundTravelTargetRemovedPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundTravelTargetUpdatedPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
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
        ListTag tag = new ListTag();
        tag.addAll(travelTargets.values().stream().map(target -> saveTarget(registries, target)).toList());
        nbt.put(TARGETS, tag);
        return nbt;
    }

    private <T extends TravelTarget> Tag saveTarget(HolderLookup.Provider lookupProvider, T target) {
        return TravelTarget.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, lookupProvider), target)
                .getOrThrow(false, message -> {});
    }

    public void loadNBT(HolderLookup.Provider lookupProvider, CompoundTag nbt) {
        Map<BlockPos, TravelTarget> loaded = new HashMap<>();
        ListTag targets = nbt.getList(TARGETS, Tag.TAG_COMPOUND);
        targets.stream()
                .map(anchorData -> (CompoundTag) anchorData)
                .map(tag -> loadTarget(lookupProvider, tag))
                .forEach(target -> loaded.put(target.pos(), target));
        travelTargets.clear();
        travelTargets.putAll(loaded);
    }

    private TravelTarget loadTarget(HolderLookup.Provider lookupProvider, Tag tag) {
        return TravelTarget.CODEC.decode(RegistryOps.create(NbtOps.INSTANCE, lookupProvider), tag)
                .getOrThrow(false, message -> {})
                .getFirst();
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
            var savedData = TravelTargetSavedData.getTravelData(serverPlayer.level());
            var serializedData = savedData.save(new CompoundTag());
            EIONetwork.sendToPlayer(serverPlayer, new ClientboundSyncTravelDataPacket(serializedData));
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            var savedData = TravelTargetSavedData.getTravelData(serverPlayer.level());
            var serializedData = savedData.save(new CompoundTag());
            EIONetwork.sendToPlayer(serverPlayer, new ClientboundSyncTravelDataPacket(serializedData));
        }
    }
}
