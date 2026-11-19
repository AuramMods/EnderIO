package com.enderio.enderio.init;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.enderio.content.armory.AutoSmeltLootModifier;
import com.enderio.enderio.content.broken_spawner.BrokenSpawnerLootModifier;
import com.enderio.enderio.content.capacitors.SetLootCapacitorFunction;
import com.enderio.enderio.content.paint.CopyPaintFunction;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@SuppressWarnings("unused")
public class EIOLootModifiers {
    private static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS = DeferredRegister
            .create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, EnderIOAPI.MOD_ID);

    private static final DeferredRegister<LootItemConditionType> CONDITIONS = DeferredRegister
            .create(Registries.LOOT_CONDITION_TYPE, EnderIOAPI.MOD_ID);
    private static final DeferredRegister<LootItemFunctionType> FUNCTIONS = DeferredRegister
            .create(Registries.LOOT_FUNCTION_TYPE, EnderIOAPI.MOD_ID);

    public static final RegistryObject<Codec<AutoSmeltLootModifier>> AUTO_SMELT =
            SERIALIZERS.register("auto_smelt", () -> AutoSmeltLootModifier.CODEC);

    public static RegistryObject<Codec<BrokenSpawnerLootModifier>> BROKEN_SPAWNER_SERIALIZER = SERIALIZERS
            .register("broken_spawner", () -> BrokenSpawnerLootModifier.CODEC);

    public static final RegistryObject<LootItemFunctionType> SET_LOOT_CAPACITOR = FUNCTIONS
            .register("set_loot_capacitor", () -> new LootItemFunctionType(new SetLootCapacitorFunction.Serializer()));

    public static final RegistryObject<LootItemFunctionType> COPY_PAINT = FUNCTIONS
            .register("copy_paint", () -> new LootItemFunctionType(new CopyPaintFunction.Serializer()));

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
        CONDITIONS.register(bus);
        FUNCTIONS.register(bus);
    }
}
