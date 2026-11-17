package com.enderio.core.common.registries;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.RegistryObject;

public class MenuDeferredRegister extends EnderDeferredRegister<MenuType<?>> {

    public static MenuDeferredRegister create(String namespace) {
        return new MenuDeferredRegister(namespace);
    }

    protected MenuDeferredRegister(String namespace) {
        super(Registries.MENU, namespace);
    }

    public <M extends AbstractContainerMenu> RegistryObject<MenuType<M>> register(String name, MenuType.MenuSupplier<M> factory) {
        return register(name, () -> new MenuType<>(factory, FeatureFlags.DEFAULT_FLAGS));
    }

    public <M extends AbstractContainerMenu> RegistryObject<MenuType<M>> register(String name, MenuType.MenuSupplier<M> factory, FeatureFlagSet featureFlags) {
        return register(name, () -> new MenuType<>(factory, featureFlags));
    }

    public <M extends AbstractContainerMenu> RegistryObject<MenuType<M>> register(String name, IContainerFactory<M> factory) {
        return register(name, () -> new MenuType<>(factory, FeatureFlags.DEFAULT_FLAGS));
    }

    public <M extends AbstractContainerMenu> RegistryObject<MenuType<M>> register(String name, IContainerFactory<M> factory, FeatureFlagSet featureFlags) {
        return register(name, () -> new MenuType<>(factory, featureFlags));
    }
}
