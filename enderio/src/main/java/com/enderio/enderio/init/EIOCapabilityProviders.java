package com.enderio.enderio.init;

import com.enderio.core.common.registries.BlockEntityTypeDeferredHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import com.enderio.enderio.api.EnderIOCapabilities;
import com.enderio.enderio.api.EnderIORegistries;
import com.enderio.enderio.content.cold_fire.ColdFireIgniter;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorBankItem;
import com.enderio.enderio.content.tools.vials.VoidVialItem;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.content.conduits.facades.ComponentBackedConduitFacadeProvider;
import com.enderio.enderio.content.filters.AbstractFilterItem;
import com.enderio.enderio.content.filters.fluid.EnderFluidFilterItem;
import com.enderio.enderio.content.filters.item.general.EnderItemFilterItem;
import com.enderio.enderio.content.filters.item.limited.LimitedItemFilterItem;
import com.enderio.enderio.content.filters.redstone.RedstoneANDFilter;
import com.enderio.enderio.content.filters.redstone.RedstoneCountFilter;
import com.enderio.enderio.content.filters.redstone.RedstoneNANDFilter;
import com.enderio.enderio.content.filters.redstone.RedstoneNORFilter;
import com.enderio.enderio.content.filters.redstone.RedstoneNOTFilter;
import com.enderio.enderio.content.filters.redstone.RedstoneORFilter;
import com.enderio.enderio.content.filters.redstone.RedstoneSensorFilter;
import com.enderio.enderio.content.filters.redstone.RedstoneTLatchFilter;
import com.enderio.enderio.content.filters.redstone.RedstoneTimerFilter;
import com.enderio.enderio.content.filters.redstone.RedstoneXNORFilter;
import com.enderio.enderio.content.filters.redstone.RedstoneXORFilter;
import com.enderio.enderio.content.filters.soul.EnderSoulFilterItem;
import com.enderio.enderio.content.storage.fluid_tank.FluidTankBlockItem;
import com.enderio.enderio.content.tools.LevitationStaffItem;
import com.enderio.enderio.content.tools.PoweredToggledItem;
import com.enderio.enderio.content.travel.TravelStaffItem;
import com.enderio.enderio.foundation.soul.SoulCapabilityProviders;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import com.enderio.core.common.capability.ItemCapabilityRegistry;
import com.enderio.enderio.EnderIO;

public class EIOCapabilityProviders {
    public static void register() {
        var registry = new ItemCapabilityRegistry(EnderIO.rl("item_capabilities"));
        // Register item energy handlers
        registry.register(ForgeCapabilities.ENERGY, PoweredToggledItem.ENERGY_STORAGE_PROVIDER,
            EIOItems.LEVITATION_STAFF.getId(), EIOItems.ELECTROMAGNET.getId());
        registry.register(ForgeCapabilities.ENERGY, TravelStaffItem.ENERGY_STORAGE_PROVIDER, EIOItems.TRAVEL_STAFF.getId());
        // Capacitor Banks
        for (var capacitorBankItem : EIOBlocks.CAPACITOR_BANK_ITEMS.values()) {
            registry.register(ForgeCapabilities.ENERGY, CapacitorBankItem.ENERGY_STORAGE_PROVIDER, capacitorBankItem.getId());
        }

        // Register item fluid handlers
        registry.register(ForgeCapabilities.FLUID_HANDLER_ITEM, LevitationStaffItem.FLUID_HANDLER_PROVIDER, EIOItems.LEVITATION_STAFF.getId());
        registry.register(ForgeCapabilities.FLUID_HANDLER_ITEM, ColdFireIgniter.FLUID_HANDLER_PROVIDER, EIOItems.COLD_FIRE_IGNITER.getId());
        registry.register(ForgeCapabilities.FLUID_HANDLER_ITEM, VoidVialItem.FLUID_HANDLER_PROVIDER, EIOItems.VOID_VIAL.getId());
        registry.register(ForgeCapabilities.FLUID_HANDLER_ITEM, FluidTankBlockItem.FLUID_HANDLER_PROVIDER,
            EIOBlocks.FLUID_TANK_ITEM.getId(), EIOBlocks.PRESSURIZED_FLUID_TANK_ITEM.getId());

        // region Filters

        // Filter menu providers
        registry.register(EnderIOCapabilities.FILTER_MENU_PROVIDER, AbstractFilterItem.FILTER_MENU_PROVIDER, EIOItems.BASIC_ITEM_FILTER.getId(),
            EIOItems.ADVANCED_ITEM_FILTER.getId(), EIOItems.BIG_ITEM_FILTER.getId(), EIOItems.BIG_ADVANCED_ITEM_FILTER.getId(), EIOItems.BASIC_SOUL_FILTER.getId(),
            EIOItems.LIMITED_ITEM_FILTER.getId());

        // Filter providers
        registry.register(EnderIOCapabilities.ITEM_FILTER, EnderItemFilterItem.ITEM_FILTER_PROVIDER, EIOItems.BASIC_ITEM_FILTER.getId(), EIOItems.ADVANCED_ITEM_FILTER.getId(),
            EIOItems.BIG_ITEM_FILTER.getId(), EIOItems.BIG_ADVANCED_ITEM_FILTER.getId());

        registry.register(EnderIOCapabilities.ITEM_FILTER, LimitedItemFilterItem.ITEM_FILTER_PROVIDER, EIOItems.LIMITED_ITEM_FILTER.getId());

        registry.register(EnderIOCapabilities.FLUID_FILTER, EnderFluidFilterItem.FLUID_FILTER_PROVIDER, EIOItems.BASIC_FLUID_FILTER.getId());

        registry.register(EnderIOCapabilities.SOUL_FILTER, EnderSoulFilterItem.ENTITY_FILTER_PROVIDER, EIOItems.BASIC_SOUL_FILTER.getId());

        registry.register(EnderIOCapabilities.REDSTONE_INSERT_FILTER, (s, c) -> RedstoneNOTFilter.INSTANCE, EIOItems.NOT_FILTER.getId());
        registry.register(EnderIOCapabilities.REDSTONE_EXTRACT_FILTER, (s, c) -> RedstoneNOTFilter.INSTANCE, EIOItems.NOT_FILTER.getId());

        registry.register(EnderIOCapabilities.REDSTONE_INSERT_FILTER, (s, c) -> new RedstoneORFilter(s), EIOItems.OR_FILTER.getId());
        registry.register(EnderIOCapabilities.REDSTONE_INSERT_FILTER, (s, c) -> new RedstoneANDFilter(s), EIOItems.AND_FILTER.getId());
        registry.register(EnderIOCapabilities.REDSTONE_INSERT_FILTER, (s, c) -> new RedstoneNORFilter(s), EIOItems.NOR_FILTER.getId());
        registry.register(EnderIOCapabilities.REDSTONE_INSERT_FILTER, (s, c) -> new RedstoneNANDFilter(s), EIOItems.NAND_FILTER.getId());
        registry.register(EnderIOCapabilities.REDSTONE_INSERT_FILTER, (s, c) -> new RedstoneXORFilter(s), EIOItems.XOR_FILTER.getId());
        registry.register(EnderIOCapabilities.REDSTONE_INSERT_FILTER, (s, c) -> new RedstoneXNORFilter(s), EIOItems.XNOR_FILTER.getId());
        registry.register(EnderIOCapabilities.REDSTONE_INSERT_FILTER, (s, c) -> new RedstoneTLatchFilter(s), EIOItems.TLATCH_FILTER.getId());
        registry.register(EnderIOCapabilities.REDSTONE_INSERT_FILTER, (s, c) -> new RedstoneCountFilter(s), EIOItems.COUNT_FILTER.getId());

        registry.register(EnderIOCapabilities.REDSTONE_EXTRACT_FILTER, (s, c) -> RedstoneSensorFilter.INSTANCE, EIOItems.SENSOR_FILTER.getId());
        registry.register(EnderIOCapabilities.REDSTONE_EXTRACT_FILTER, (s, c) -> new RedstoneTimerFilter(s), EIOItems.TIMER_FILTER.getId());

        // endregion

        // TODO: Testing the waters on doing capabilities by hand.
        // Register soul bindable items
        registry.register(EnderIOCapabilities.SOUL_BINDABLE_ITEM,
            SoulCapabilityProviders.COMPONENT_SOUL_BINDABLE_PROVIDER,
            EIOItems.BROKEN_SPAWNER.getId(),
            EIOBlocks.POWERED_SPAWNER_ITEM.getId(),
            EIOBlocks.SOUL_ENGINE_ITEM.getId(),
            EIOBlocks.FARMING_STATION.getId());
        
        // Solar Panels (soul bindable)
        for (var solarPanelItem : EIOBlocks.SOLAR_PANEL_ITEMS.values()) {
            registry.register(EnderIOCapabilities.SOUL_BINDABLE_ITEM,
                SoulCapabilityProviders.COMPONENT_SOUL_BINDABLE_PROVIDER,
                solarPanelItem.getId());
        }

        // Register read-only soul bindable items
        // Soul vial uses a read-only ISoulBindable because the soul vial is a storage which can be used for binding, but is not directly bound to.
        registry.register(EnderIOCapabilities.SOUL_BINDABLE_ITEM,
            SoulCapabilityProviders.READ_ONLY_COMPONENT_SOUL_BINDABLE_PROVIDER,
            EIOItems.SOUL_VIAL.getId());

        // Register single soul handler items
        registry.register(EnderIOCapabilities.SOUL_HANDLER_ITEM,
            SoulCapabilityProviders.SINGLE_COMPONENT_SOUL_HANDLER_PROVIDER,
            EIOItems.SOUL_VIAL.getId());

        // Conduit facades
        registry.register(EnderIOCapabilities.CONDUIT_FACADE_PROVIDER, ComponentBackedConduitFacadeProvider.PROVIDER,
            EIOItems.CONDUIT_FACADE.getId(), EIOItems.TRANSPARENT_CONDUIT_FACADE.getId(), EIOItems.HARDENED_CONDUIT_FACADE.getId(), EIOItems.TRANSPARENT_HARDENED_CONDUIT_FACADE.getId());
        registry.register();
    }

    // region Conduits

    @EventBusSubscriber(modid = "enderio", bus = EventBusSubscriber.Bus.FORGE)
    public static class ConduitCapabilities {
        @SubscribeEvent
        public static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
            if (!(event.getObject() instanceof ConduitBundleBlockEntity bundle)) return;
            List<BlockEntityTypeDeferredHolder.AttachedCapability<ConduitBundleBlockEntity, ?>> bindings = new ArrayList<>();
            EnderIORegistries.CONDUIT_TYPE.get().getValues().stream()
                .flatMap(type -> type.exposedCapabilities().stream())
                .distinct()
                .forEach(capability -> bindings.add(binding(capability)));
            var provider = new BlockEntityTypeDeferredHolder.BoundCapabilities<>(bundle, bindings);
            event.addCapability(ResourceLocation.fromNamespaceAndPath("enderio", "conduit_capabilities"), provider);
            event.addListener(provider::invalidate);
        }

        private static <T> BlockEntityTypeDeferredHolder.AttachedCapability<ConduitBundleBlockEntity, T> binding(Capability<T> capability) {
            return new BlockEntityTypeDeferredHolder.AttachedCapability<>(capability,
                ConduitBundleBlockEntity.createCapabilityProvider(capability));
        }
    }

    // endregion
}
