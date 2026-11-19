package com.enderio.enderio.init;

import com.enderio.enderio.EnderIO;
import com.enderio.enderio.content.armory.SoulboundEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class EIOEnchantments {
    private static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, EnderIO.MOD_ID);

    public static final RegistryObject<Enchantment> SOULBOUND = ENCHANTMENTS.register("soulbound", SoulboundEnchantment::new);

    private EIOEnchantments() {}

    public static void register(IEventBus bus) {
        ENCHANTMENTS.register(bus);
    }
}
