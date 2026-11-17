package com.enderio.enderio.api;

import com.enderio.enderio.api.capacitor.ICapacitorExtension;
import com.enderio.enderio.api.conduits.facade.ConduitFacadeProvider;
import com.enderio.enderio.api.filter.FilterMenuProvider;
import com.enderio.enderio.api.filter.FluidFilter;
import com.enderio.enderio.api.filter.ItemFilter;
import com.enderio.enderio.api.filter.RedstoneInputFilter;
import com.enderio.enderio.api.filter.RedstoneOutputFilter;
import com.enderio.enderio.api.filter.SoulFilter;
import com.enderio.enderio.api.io.SideConfig;
import com.enderio.enderio.api.soul.binding.SoulBindable;
import com.enderio.enderio.api.soul.storage.SoulHandler;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = "enderio", bus = EventBusSubscriber.Bus.MOD)
public class EnderIOCapabilities {

    public static final Capability<SideConfig> SIDE_CONFIG = SideConfig.CAPABILITY;

    public static final Capability<ICapacitorExtension> CAPACITOR_EXTENSION = ICapacitorExtension.CAPABILITY;

    public static final Capability<FilterMenuProvider> FILTER_MENU_PROVIDER = CapabilityManager.get(new CapabilityToken<>() {});

    public static final Capability<ItemFilter> ITEM_FILTER = CapabilityManager.get(new CapabilityToken<>() {});

    public static final Capability<FluidFilter> FLUID_FILTER = CapabilityManager.get(new CapabilityToken<>() {});

    public static final Capability<SoulFilter> SOUL_FILTER = CapabilityManager.get(new CapabilityToken<>() {});

    public static final Capability<SoulBindable> SOUL_BINDABLE_ITEM = SoulBindable.CAPABILITY;

    public static final Capability<SoulBindable> SOUL_BINDABLE_BLOCK = SOUL_BINDABLE_ITEM;

    public static final Capability<SoulHandler> SOUL_HANDLER_ITEM = SoulHandler.CAPABILITY;

    public static final Capability<SoulHandler> SOUL_HANDLER_BLOCK = SOUL_HANDLER_ITEM;

    public static final Capability<ConduitFacadeProvider> CONDUIT_FACADE_PROVIDER = CapabilityManager.get(new CapabilityToken<>() {});

    public static final Capability<RedstoneOutputFilter> REDSTONE_INSERT_FILTER = CapabilityManager.get(new CapabilityToken<>() {});

    public static final Capability<RedstoneInputFilter> REDSTONE_EXTRACT_FILTER = CapabilityManager.get(new CapabilityToken<>() {});

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(SideConfig.class);
        event.register(ICapacitorExtension.class);
        event.register(FilterMenuProvider.class);
        event.register(ItemFilter.class);
        event.register(FluidFilter.class);
        event.register(SoulFilter.class);
        event.register(SoulBindable.class);
        event.register(SoulHandler.class);
        event.register(ConduitFacadeProvider.class);
        event.register(RedstoneOutputFilter.class);
        event.register(RedstoneInputFilter.class);
    }
}
