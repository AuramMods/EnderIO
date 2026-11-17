package com.enderio.modded_conduits.client;

import com.enderio.modded_conduits.client.modules.mekanism.MekanismClientModule;
import com.enderio.modded_conduits.common.ModdedConduits;
import com.enderio.modded_conduits.common.ModuleModIds;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

import java.util.Map;
import java.util.function.Supplier;

public class ModdedConduitsClient {

    private static final Map<String, Supplier<ConduitClientModule>> CLIENT_MODULES = Map
            .ofEntries(Map.entry(ModuleModIds.MEKANISM, () -> MekanismClientModule.INSTANCE));

    public static void initialize(IEventBus modEventBus) {
        for (var module : CLIENT_MODULES.entrySet()) {
            if (ModList.get().isLoaded(module.getKey())) {
                module.getValue().get().initialize(modEventBus);
            }
        }
    }
}
