package com.enderio.enderio.datagen;

import com.enderio.enderio.EnderIO;
import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.enderio.api.EnderIORegistries;
import com.enderio.enderio.datagen.client.EIOLanguageProvider;
import com.enderio.enderio.datagen.client.models.EIOAthenaBlockStateProvider;
import com.enderio.enderio.datagen.client.models.EIOBlockStateProvider;
import com.enderio.enderio.datagen.client.models.EIOItemModelProvider;
import com.enderio.enderio.datagen.common.advancement.EIOAdvancementGenerator;
import com.enderio.enderio.datagen.common.advancement.MachinesAdvancementGenerator;
import com.enderio.enderio.datagen.common.data_maps.GrindingBallDataMapProvider;
import com.enderio.enderio.datagen.common.data_maps.RangeExtenderDataMapProvider;
import com.enderio.enderio.datagen.common.data_maps.ReagentDataMapProvider;
import com.enderio.enderio.datagen.common.datapack_registries.ConduitsBootstrap;
import com.enderio.enderio.datagen.common.loot.ChestLootProvider;
import com.enderio.enderio.datagen.common.loot.EIOBlockLootProvider;
import com.enderio.enderio.datagen.common.loot.EIOLootModifiersProvider;
import com.enderio.enderio.datagen.common.recipes.EnderIORecipeProvider;
import com.enderio.enderio.datagen.common.souldata.SoulDataProvider;
import com.enderio.enderio.datagen.client.sounds.EIOSoundDefinitionProvider;
import com.enderio.enderio.datagen.common.tags.EIOBlockTagsProvider;
import com.enderio.enderio.datagen.common.tags.EIOEntityTagsProvider;
import com.enderio.enderio.datagen.common.tags.EIOFluidTagsProvider;
import com.enderio.enderio.datagen.common.tags.EIOItemTagsProvider;
import com.enderio.enderio.foundation.lang.EIOCommonLang;
import net.minecraft.DetectedVersion;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.data.metadata.PackMetadataGenerator;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.data.ForgeAdvancementProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.data.DatapackBuiltinEntriesProvider;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = EnderIOAPI.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class EnderIODataGen {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = event.getGenerator().getPackOutput();
        var datapacks = new DatapackBuiltinEntriesProvider(packOutput, event.getLookupProvider(),
            createDatapackEntriesBuilder(), Set.of(EnderIO.MOD_ID));
        generator.addProvider(event.includeServer(), datapacks);
        CompletableFuture<HolderLookup.Provider> lookupProvider = datapacks.getRegistryProvider();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();

        var b = new EIOBlockTagsProvider(packOutput, lookupProvider, existingFileHelper);
        generator.addProvider(event.includeServer(), b);
        generator.addProvider(event.includeServer(),
            new EIOItemTagsProvider(packOutput, lookupProvider, b.contentsGetter(), existingFileHelper));
        generator.addProvider(event.includeServer(),
            new EIOFluidTagsProvider(packOutput, lookupProvider, existingFileHelper));
        generator.addProvider(event.includeServer(),
            new EIOEntityTagsProvider(packOutput, lookupProvider, existingFileHelper));

        generator.addProvider(event.includeServer(), new ForgeAdvancementProvider(packOutput, lookupProvider,
            existingFileHelper, List.of(new EIOAdvancementGenerator(), new MachinesAdvancementGenerator())));

        generator.addProvider(event.includeServer(), new EnderIORecipeProvider(packOutput, lookupProvider));

        generator.addProvider(event.includeServer(), new GrindingBallDataMapProvider(packOutput, lookupProvider));
        generator.addProvider(event.includeServer(), new ReagentDataMapProvider(packOutput, lookupProvider));
        generator.addProvider(event.includeServer(), new RangeExtenderDataMapProvider(packOutput, lookupProvider));

        generator.addProvider(event.includeServer(), new SoulDataProvider(packOutput));

        generator.addProvider(event.includeServer(), new EIOLootModifiersProvider(packOutput, lookupProvider));

        generator.addProvider(event.includeServer(),
            new LootTableProvider(packOutput, Collections.emptySet(), List.of(
                new LootTableProvider.SubProviderEntry(EIOBlockLootProvider::new, LootContextParamSets.BLOCK),
                new LootTableProvider.SubProviderEntry(ChestLootProvider::new, LootContextParamSets.CHEST)
            )));

        generator.addProvider(event.includeClient(), new EIOItemModelProvider(packOutput, existingFileHelper));
        generator.addProvider(event.includeClient(), new EIOBlockStateProvider(packOutput, existingFileHelper));
        generator.addProvider(event.includeClient(), new EIOLanguageProvider(packOutput));
        generator.addProvider(event.includeClient(), new EIOSoundDefinitionProvider(packOutput, existingFileHelper));

        var athenaOutput = generator.getPackOutput(EnderIO.ATHENA_RESOURCE_PACK_PATH);
        generator.addProvider(event.includeClient(), new EIOAthenaBlockStateProvider(athenaOutput, existingFileHelper));
        generator.addProvider(event.includeClient(), new PackMetadataGenerator(athenaOutput).add(PackMetadataSection.TYPE,
            new PackMetadataSection(EIOCommonLang.RESOURCE_PACK_ATHENA,
                DetectedVersion.BUILT_IN.getPackVersion(PackType.CLIENT_RESOURCES))));
    }

    private static RegistrySetBuilder createDatapackEntriesBuilder() {
        return new RegistrySetBuilder()
            .add(EnderIORegistries.Keys.CONDUIT, ConduitsBootstrap::bootstrap);
    }
}
