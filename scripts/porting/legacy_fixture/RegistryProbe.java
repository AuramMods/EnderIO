package porting.registryprobe;

import com.google.gson.GsonBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryManager;
import net.minecraftforge.forgespi.language.IModInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

@Mod("port_registry_probe")
public class RegistryProbe {
    private static final List<String> FORGE_REGISTRIES = List.of("block", "item", "fluid", "entity_type",
        "block_entity_type", "recipe_serializer", "recipe_type", "enchantment", "menu", "sound_event", "particle_type");
    private static final List<String> ENDERIO_REGISTRIES = List.of("conduit_type", "conduit_data_type", "conduit_node_data_type",
        "conduit_connection_config_type", "conduit_network_context_type", "travel_target_types", "travel_target_serializers");

    public RegistryProbe() {
        MinecraftForge.EVENT_BUS.addListener(this::register);
    }

    private void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("port_dump_registries").requires(source -> source.hasPermission(4))
            .executes(context -> dump(context.getSource())));
    }

    private int dump(CommandSourceStack source) {
        var server = source.getServer();
        var flags = server.getWorldData().enabledFeatures();
        var recipes = new TreeMap<String, String>();
        server.getRecipeManager().getRecipes().forEach(recipe -> {
            if (recipe.getId().getNamespace().equals("enderio")) {
                recipes.put(recipe.getId().toString(), ForgeRegistries.RECIPE_SERIALIZERS.getKey(recipe.getSerializer()).toString());
            }
        });

        var registries = new TreeMap<String, List<String>>();
        FORGE_REGISTRIES.forEach(name -> collectForgeRegistry(registries, new ResourceLocation("minecraft", name)));
        ENDERIO_REGISTRIES.forEach(name -> collectForgeRegistry(registries, new ResourceLocation("enderio", name)));
        server.registryAccess().registries().forEach(entry ->
            registries.put(entry.key().location().toString(), enderIOIds(entry.value().keySet().stream())));

        var tags = new TreeMap<String, Map<String, Integer>>();
        collectTags(tags, BuiltInRegistries.BLOCK);
        collectTags(tags, BuiltInRegistries.ITEM);
        collectTags(tags, BuiltInRegistries.FLUID);
        collectTags(tags, BuiltInRegistries.ENTITY_TYPE);
        server.registryAccess().registries().forEach(entry -> collectTags(tags, entry.value()));

        var snapshot = new TreeMap<String, Object>();
        snapshot.put("mods", ModList.get().getMods().stream().map(IModInfo::getModId).sorted().toList());
        snapshot.put("features", FeatureFlags.REGISTRY.toNames(flags).stream().map(ResourceLocation::toString).sorted().toList());
        snapshot.put("packs", server.getPackRepository().getSelectedIds().stream().sorted().toList());
        snapshot.put("recipes", recipes);
        snapshot.put("smeltingRecipes", server.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING).stream()
            .filter(recipe -> !recipe.getResultItem(server.registryAccess()).isEmpty() && recipe.getIngredients().size() == 1
                && !recipe.getIngredients().get(0).isEmpty())
            .map(recipe -> recipe.getId().toString()).sorted().toList());
        snapshot.put("registries", registries);
        snapshot.put("tags", tags);
        snapshot.put("enabledBlocks", enderIOIds(ForgeRegistries.BLOCKS.getEntries().stream()
            .filter(entry -> entry.getValue().isEnabled(flags)).map(entry -> entry.getKey().location())));
        snapshot.put("enabledItems", enderIOIds(ForgeRegistries.ITEMS.getEntries().stream()
            .filter(entry -> entry.getValue().isEnabled(flags)).map(entry -> entry.getKey().location())));
        snapshot.put("advancements", enderIOIds(server.getAdvancements().getAllAdvancements().stream()
            .map(advancement -> advancement.getId())));
        snapshot.put("lootTables", enderIOIds(server.getLootData().getKeys(LootDataType.TABLE).stream()));
        try (var writer = Files.newBufferedWriter(Path.of("registry-snapshot.json"))) {
            new GsonBuilder().setPrettyPrinting().create().toJson(snapshot, writer);
        } catch (IOException error) {
            source.sendFailure(Component.literal("Unable to write registry snapshot: " + error.getMessage()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Packaged registry snapshot written"), false);
        return 1;
    }

    private static void collectForgeRegistry(Map<String, List<String>> registries, ResourceLocation key) {
        var registry = RegistryManager.ACTIVE.getRegistry(key);
        if (registry != null) registries.put(key.toString(), enderIOIds(registry.getKeys().stream()));
    }

    private static void collectTags(Map<String, Map<String, Integer>> tags, Registry<?> registry) {
        var entries = new TreeMap<String, Integer>();
        registry.getTags().forEach(tag -> entries.put(tag.getFirst().location().toString(), tag.getSecond().size()));
        if (!entries.isEmpty()) tags.put(registry.key().location().getPath(), entries);
    }

    private static List<String> enderIOIds(Stream<ResourceLocation> ids) {
        return ids.filter(id -> id.getNamespace().equals("enderio")).map(ResourceLocation::toString).sorted().toList();
    }
}
