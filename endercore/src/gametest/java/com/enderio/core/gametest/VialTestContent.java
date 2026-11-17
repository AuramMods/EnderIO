package com.enderio.core.gametest;

import com.enderio.core.common.capability.ItemCapabilityRegistry;
import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.enderio.api.soul.binding.SoulBindable;
import com.enderio.enderio.content.tools.vials.SoulVialItem;
import com.enderio.enderio.foundation.soul.SoulCapabilityProviders;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class VialTestContent {
    private static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "endercore_tests");
    static final RegistryObject<DispenserAccess> DISPENSER_ACCESS = BLOCKS.register("vial_dispenser_access", DispenserAccess::new);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "enderio");
    static final RegistryObject<SoulVialItem> SOUL_VIAL = ITEMS.register("soul_vial", () -> new SoulVialItem(new Item.Properties()));

    static void register(IEventBus bus) {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        bus.addListener((RegisterCapabilitiesEvent event) -> event.register(SoulBindable.class));
        bus.register(SoulVialItem.Registration.class);
        MinecraftForge.EVENT_BUS.register(SoulVialItem.class);
        MinecraftForge.EVENT_BUS.register(com.enderio.enderio.foundation.util.EntityCaptureUtils.class);
        var capabilities = new ItemCapabilityRegistry(EnderIOAPI.rl("vial_test_capabilities"));
        capabilities.register(SoulBindable.CAPABILITY, SoulCapabilityProviders.READ_ONLY_COMPONENT_SOUL_BINDABLE_PROVIDER, SOUL_VIAL.getId());
        capabilities.register();
    }
    /** Only exposes the native registered behavior; tests place a real vanilla dispenser. */
    static final class DispenserAccess extends net.minecraft.world.level.block.DispenserBlock {
        DispenserAccess() { super(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()); }
        net.minecraft.core.dispenser.DispenseItemBehavior behavior(net.minecraft.world.item.ItemStack stack) { return getDispenseMethod(stack); }
    }
}
