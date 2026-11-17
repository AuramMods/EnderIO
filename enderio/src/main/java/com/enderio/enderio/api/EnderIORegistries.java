package com.enderio.enderio.api;

import com.enderio.enderio.api.conduits.Conduit;
import com.enderio.enderio.api.conduits.ConduitType;
import com.enderio.enderio.api.conduits.connection.config.ConnectionConfigType;
import com.enderio.enderio.api.conduits.network.ConduitNetworkContextType;
import com.enderio.enderio.api.conduits.network.node.NodeDataType;
import com.enderio.enderio.api.conduits.network.node.legacy.ConduitDataType;
import com.enderio.enderio.api.travel.TravelTargetSerializer;
import com.enderio.enderio.api.travel.TravelTargetType;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class EnderIORegistries {

    private static final List<DeferredRegister<?>> REGISTRIES = new ArrayList<>();

    // Forge creates custom registries during NewRegistryEvent, after mod construction.
    // Codecs and addon deferred registers must not resolve these suppliers beforehand.
    public static final Supplier<IForgeRegistry<TravelTargetType<?>>> TRAVEL_TARGET_TYPES = create(Keys.TRAVEL_TARGET_TYPES);
    public static final Supplier<IForgeRegistry<TravelTargetSerializer<?>>> TRAVEL_TARGET_SERIALIZERS = create(Keys.TRAVEL_TARGET_SERIALIZERS);
    public static final Supplier<IForgeRegistry<ConduitType<?, ?>>> CONDUIT_TYPE = create(Keys.CONDUIT_TYPE);

    @Deprecated(since = "8.0.0")
    public static final Supplier<IForgeRegistry<ConduitDataType<?>>> CONDUIT_DATA_TYPE = create(Keys.CONDUIT_DATA_TYPE);

    public static final Supplier<IForgeRegistry<NodeDataType<?>>> CONDUIT_NODE_DATA_TYPE = create(Keys.CONDUIT_NODE_DATA_TYPE);
    public static final Supplier<IForgeRegistry<ConnectionConfigType<?>>> CONDUIT_CONNECTION_CONFIG_TYPE = create(Keys.CONDUIT_CONNECTION_CONFIG_TYPE);
    public static final Supplier<IForgeRegistry<ConduitNetworkContextType<?>>> CONDUIT_NETWORK_CONTEXT_TYPE = create(Keys.CONDUIT_NETWORK_CONTEXT_TYPE);

    private static <T> Supplier<IForgeRegistry<T>> create(ResourceKey<Registry<T>> key) {
        DeferredRegister<T> register = DeferredRegister.create(key, EnderIOAPI.MOD_ID);
        REGISTRIES.add(register);
        // Forge registries save and synchronize numeric IDs by default.
        return register.makeRegistry(RegistryBuilder::new);
    }

    public static void register(IEventBus bus) {
        REGISTRIES.forEach(register -> register.register(bus));
    }

    public static <T> Codec<T> codec(Supplier<IForgeRegistry<T>> registry) {
        return ExtraCodecs.lazyInitializedCodec(() -> registry.get().getCodec());
    }

    public static class Keys {
        public static final ResourceKey<Registry<TravelTargetType<?>>> TRAVEL_TARGET_TYPES = createKey("travel_target_types");
        public static final ResourceKey<Registry<TravelTargetSerializer<?>>> TRAVEL_TARGET_SERIALIZERS = createKey("travel_target_serializers");

        @Deprecated(since = "8.0.0")
        public static final ResourceKey<Registry<ConduitDataType<?>>> CONDUIT_DATA_TYPE = createKey(
                "conduit_data_type");

        public static final ResourceKey<Registry<NodeDataType<?>>> CONDUIT_NODE_DATA_TYPE = createKey(
                "conduit_node_data_type");
        public static final ResourceKey<Registry<ConnectionConfigType<?>>> CONDUIT_CONNECTION_CONFIG_TYPE = createKey(
                "conduit_connection_config_type");
        public static final ResourceKey<Registry<ConduitNetworkContextType<?>>> CONDUIT_NETWORK_CONTEXT_TYPE = createKey(
                "conduit_network_context_type");

        public static final ResourceKey<Registry<ConduitType<?, ?>>> CONDUIT_TYPE = createKey("conduit_type");

        /**
         * Conduit types are now a datapack registry.
         */
        public static final ResourceKey<Registry<Conduit<?, ?>>> CONDUIT = createKey("conduit");

        private static <T> ResourceKey<Registry<T>> createKey(String name) {
            return ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("enderio", name));
        }
    }
}
