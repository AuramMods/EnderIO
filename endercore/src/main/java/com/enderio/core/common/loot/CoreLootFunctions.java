package com.enderio.core.common.loot;

import com.enderio.core.EnderCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class CoreLootFunctions {
    private static final DeferredRegister<LootItemFunctionType> FUNCTIONS = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, EnderCore.MOD_ID);
    public static final RegistryObject<LootItemFunctionType> COPY_BLOCK_ENTITY_DATA = FUNCTIONS.register("copy_block_entity_data",
        () -> new LootItemFunctionType(new CopyBlockEntityDataFunction.Serializer()));

    private CoreLootFunctions() {}

    public static void register(IEventBus bus) { FUNCTIONS.register(bus); }
}
