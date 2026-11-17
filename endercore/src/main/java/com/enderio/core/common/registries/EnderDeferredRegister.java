package com.enderio.core.common.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;

/** Shared registration conveniences backed by Forge's deferred register. */
public class EnderDeferredRegister<T> {
    private final DeferredRegister<T> delegate;
    private final String namespace;

    protected EnderDeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        delegate = DeferredRegister.create(registryKey, namespace);
        this.namespace = namespace;
    }

    public <I extends T> RegistryObject<I> register(String name, Supplier<? extends I> factory) {
        return delegate.register(name, factory);
    }

    public <I extends T> RegistryObject<I> register(String name, Function<ResourceLocation, ? extends I> factory) {
        return register(name, () -> factory.apply(new ResourceLocation(namespace, name)));
    }

    public void register(IEventBus bus) {
        delegate.register(bus);
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
