package com.enderio.core.common.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryManager;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/** Shared registration conveniences backed by Forge's deferred register. */
public class EnderDeferredRegister<T> {
    private final DeferredRegister<T> delegate;
    private final String namespace;
    private final Map<ResourceLocation, ResourceLocation> aliases = new LinkedHashMap<>();

    protected EnderDeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        delegate = DeferredRegister.create(registryKey, namespace);
        this.namespace = namespace;
    }

    public <I extends T> RegistryObject<I> register(String name, Supplier<? extends I> factory) {
        return delegate.register(name, factory);
    }

    public <I extends T> RegistryObject<I> register(String name, Function<ResourceLocation, ? extends I> factory) {
        return register(name, () -> factory.apply(ResourceLocation.fromNamespaceAndPath(namespace, name)));
    }

    public void register(IEventBus bus) {
        delegate.register(bus);
        bus.addListener(EventPriority.LOW, this::registerAliases);
        MinecraftForge.EVENT_BUS.addListener(this::remapSavedAliases);
    }

    /** Forge persists these aliases in registry snapshots and uses them for old saved IDs. */
    public void addAlias(ResourceLocation oldId, ResourceLocation newId) {
        aliases.put(oldId, newId);
    }

    private void registerAliases(RegisterEvent event) {
        if (event.getRegistryKey().equals(getRegistryKey()) && !aliases.isEmpty()) {
            var registry = RegistryManager.ACTIVE.getRegistry(getRegistryKey());
            aliases.forEach(registry::addAlias);
        }
    }

    private void remapSavedAliases(MissingMappingsEvent event) {
        for (var mapping : event.getAllMappings(getRegistryKey())) {
            var alias = aliases.get(mapping.getKey());
            if (alias != null) {
                var registry = RegistryManager.ACTIVE.getRegistry(getRegistryKey());
                if (registry.containsKey(alias)) {
                    mapping.remap(registry.getValue(alias));
                }
            }
        }
    }

    public Collection<RegistryObject<T>> getEntries() {
        return delegate.getEntries();
    }

    public ResourceKey<? extends Registry<T>> getRegistryKey() {
        return delegate.getRegistryKey();
    }

    public String getNamespace() {
        return namespace;
    }
}
