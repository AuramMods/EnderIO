package com.enderio.enderio.init;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.enderio.content.armory.AutoSmeltEnchantment;
import com.enderio.enderio.content.armory.RepellentEnchantment;
import com.enderio.enderio.content.armory.SoulboundEnchantment;
import com.enderio.enderio.content.armory.WitheringEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class EIOEnchantments {
    private static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, EnderIOAPI.MOD_ID);

    public static final RegistryObject<Enchantment> AUTO_SMELT = ENCHANTMENTS.register("auto_smelt", AutoSmeltEnchantment::new);
    public static final RegistryObject<Enchantment> REPELLENT = ENCHANTMENTS.register("repellent", RepellentEnchantment::new);
    public static final RegistryObject<Enchantment> SOULBOUND = ENCHANTMENTS.register("soulbound", SoulboundEnchantment::new);
    public static final RegistryObject<Enchantment> WITHERING = ENCHANTMENTS.register("withering", WitheringEnchantment::new);

    private EIOEnchantments() {}

    public static void register(IEventBus bus) {
        ENCHANTMENTS.register(bus);
    }
}
