package com.enderio.modded_conduits.common;

import com.enderio.enderio.EnderIO;
import com.enderio.enderio.api.EnderIORegistries;
import com.enderio.modded_conduits.common.modules.ConduitCommonModule;
import com.enderio.modded_conduits.client.ModdedConduitsClient;
import com.enderio.modded_conduits.common.modules.appeng.AE2ConduitsModule;
import com.enderio.modded_conduits.common.modules.mekanism.MekanismModule;
import com.enderio.modded_conduits.common.modules.refinedstorage.RefinedStorageCommonModule;
import com.enderio.modded_conduits.config.ModdedConduitsConfig;
import com.enderio.modded_conduits.data.ModConduitRecipeProvider;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.data.event.GatherDataEvent;

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Map.entry;

@Mod(value = ModdedConduits.MOD_ID)
@EventBusSubscriber(modid = ModdedConduits.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModdedConduits {
    public static final String MOD_ID = EnderIO.MOD_ID + "_modded_conduits";

    // TODO: 1.22 - our own mod id.

    private static final Map<String, Supplier<ConduitCommonModule>> CONDUIT_MODULES = Map.ofEntries(
            entry(ModuleModIds.APPLIED_ENERGISTICS, () -> AE2ConduitsModule.INSTANCE),
            entry(ModuleModIds.MEKANISM, () -> MekanismModule.INSTANCE),
            entry(ModuleModIds.REFINED_STORAGE, () -> RefinedStorageCommonModule.INSTANCE));

    public static IEventBus modEventBus;

    public ModdedConduits() {
        var context = FMLJavaModLoadingContext.get();
        modEventBus = context.getModEventBus();
        
        // Register config files
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModdedConduitsConfig.COMMON_SPEC, "enderio/modded-conduits-common.toml");
        
        executeOnLoadedModules(module -> module.initialize(modEventBus));
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ModdedConduitsClient.initialize(modEventBus));
    }

    public static void buildConduitConditions(BiConsumer<ResourceKey<?>, ICondition> conditions) {
        executeOnLoadedModules(module -> module.buildConduitConditions(conditions));
    }

    public static void executeOnLoadedModules(Consumer<ConduitCommonModule> action) {
        for (var module : CONDUIT_MODULES.entrySet()) {
            if (ModList.get().isLoaded(module.getKey())) {
                action.accept(module.getValue().get());
            }
        }
    }

    @SubscribeEvent
    public static void onData(GatherDataEvent event) {
        PackOutput packOutput = event.getGenerator().getPackOutput();
        var conditions = new java.util.HashMap<ResourceKey<?>, ICondition>();
        buildConduitConditions(conditions::put);
        var entries = new com.enderio.core.data.ConditionalRegistriesProvider(packOutput, event.getLookupProvider(),
            createDatapackEntriesBuilder(), Set.of(EnderIO.MOD_ID), conditions, "Modded Conduit Registries");
        event.getGenerator().addProvider(event.includeServer(), entries);
        var registries = entries.getRegistryProvider();

        event.getGenerator()
            .addProvider(event.includeServer(), new ModConduitRecipeProvider(packOutput, registries));
    }

    private static RegistrySetBuilder createDatapackEntriesBuilder() {
        return new RegistrySetBuilder().add(EnderIORegistries.Keys.CONDUIT,
            (context) -> ModdedConduits.executeOnLoadedModules(module -> module.bootstrapConduits(context)));
    }
}
