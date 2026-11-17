package com.enderio.core.common.loot;

import com.enderio.core.EnderCore;
import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class CoreLootModifiers {
    private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> TYPES =
        DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, EnderCore.MOD_ID);

    static { TYPES.register("add_table", () -> AddTableLootModifier.CODEC); }

    private CoreLootModifiers() {}
    public static void register(IEventBus bus) { TYPES.register(bus); }
}
