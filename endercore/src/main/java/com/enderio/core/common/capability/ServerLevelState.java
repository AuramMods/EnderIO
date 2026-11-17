package com.enderio.core.common.capability;

import com.enderio.core.EnderCore;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A transient, lazily created service owned by one server level. Access on the
 * server thread. Persistent world data belongs in SavedData instead.
 *
 * The level owns the capability, so services may reference their level or its
 * block entities without a static cache retaining worlds after shutdown.
 */
@Mod.EventBusSubscriber(modid = EnderCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerLevelState<T> {
    public static final Capability<Storage> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});
    private final Supplier<T> factory;

    public ServerLevelState(Supplier<T> factory) {
        this.factory = Objects.requireNonNull(factory);
    }

    public T get(ServerLevel level) {
        return level.getCapability(CAPABILITY)
            .orElseThrow(() -> new IllegalStateException("Missing EnderCore level state in " + level.dimension().location()))
            .get(this);
    }

    /** Returns an existing service without invoking its factory. */
    public @Nullable T getIfPresent(ServerLevel level) {
        var storage = level.getCapability(CAPABILITY).orElse(null);
        return storage == null ? null : storage.getIfPresent(this);
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Level> event) {
        if (event.getObject() instanceof ServerLevel) {
            var storage = new Storage();
            event.addCapability(EnderCore.loc("level_state"), storage);
            event.addListener(storage::invalidate);
        }
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        // Forge 1.20.1 closes levels without calling invalidateCaps().
        if (event.getLevel() instanceof ServerLevel level) {
            level.getCapability(CAPABILITY).ifPresent(Storage::invalidate);
        }
    }

    @AutoRegisterCapability
    public static final class Storage implements ICapabilityProvider {
        private final Map<ServerLevelState<?>, Object> values = new IdentityHashMap<>();
        private final LazyOptional<Storage> capability = LazyOptional.of(() -> this);
        private boolean active = true;

        private Storage() {}

        @SuppressWarnings("unchecked")
        public <T> T get(ServerLevelState<T> key) {
            if (!active) {
                throw new IllegalStateException("Level state has been invalidated");
            }
            return (T) values.computeIfAbsent(key, ignored -> Objects.requireNonNull(key.factory.get()));
        }

        @SuppressWarnings("unchecked")
        public <T> @Nullable T getIfPresent(ServerLevelState<T> key) {
            return (T) values.get(key);
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            return CAPABILITY.orEmpty(cap, capability);
        }

        void invalidate() {
            active = false;
            values.clear();
            capability.invalidate();
        }
    }
}
