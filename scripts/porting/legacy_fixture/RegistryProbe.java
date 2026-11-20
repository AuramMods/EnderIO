package porting.registryprobe;

import com.enderio.core.common.datamap.DataMaps;
import com.enderio.core.common.datamap.RegistryDataMap;
import com.enderio.enderio.content.machines.sag_mill.SagMillingRecipe;
import com.enderio.enderio.foundation.souldata.EngineSoul;
import com.enderio.enderio.foundation.souldata.FarmSoul;
import com.enderio.enderio.foundation.souldata.SolarSoul;
import com.enderio.enderio.foundation.souldata.SpawnerSoul;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
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
        event.getDispatcher().register(Commands.literal("port_use_fixture_item").requires(source -> source.hasPermission(4))
            .then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.argument("item", ResourceLocationArgument.id())
                    .executes(context -> useItem(context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "pos"),
                        ResourceLocationArgument.getId(context, "item"))))));
    }

    private int useItem(CommandSourceStack source, BlockPos pos, ResourceLocation itemId) {
        var item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null) {
            source.sendFailure(Component.literal("Fixture item unavailable: " + itemId));
            return 0;
        }
        var level = source.getLevel();
        var player = FakePlayerFactory.getMinecraft(level);
        player.moveTo(pos.getX() + 2.0, pos.getY(), pos.getZ() + 0.5, 90, 0);
        var stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.EAST, 0.5), Direction.EAST, pos, false);
        var result = player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND, hit);
        source.sendSuccess(() -> Component.literal("Native fixture item used: " + result), false);
        return 1;
    }

    private int dump(CommandSourceStack source) {
        var server = source.getServer();
        var flags = server.getWorldData().enabledFeatures();
        var recipes = new TreeMap<String, String>();
        var sagRecipes = new TreeMap<String, Object>();
        server.getRecipeManager().getRecipes().forEach(recipe -> {
            if (recipe.getId().getNamespace().equals("enderio")) {
                String id = recipe.getId().toString();
                recipes.put(id, ForgeRegistries.RECIPE_SERIALIZERS.getKey(recipe.getSerializer()).toString());
                if (recipe instanceof SagMillingRecipe sag) {
                    var inputs = Stream.of(sag.input().getItems()).map(RegistryProbe::itemId).distinct().sorted().toList();
                    var outputs = sag.outputs().stream().map(output -> {
                        var stack = output.getItemStack();
                        return Map.of("item", itemId(stack), "count", stack.getCount(), "chance", output.chance(),
                            "optional", output.isOptional());
                    }).toList();
                    StringRepresentable bonus = sag.bonusType();
                    sagRecipes.put(id, Map.of("inputs", inputs, "outputs", outputs,
                        "energy", sag.getBaseEnergyCost(), "bonus", bonus.getSerializedName()));
                }
            }
        });

        var registries = new TreeMap<String, List<String>>();
        FORGE_REGISTRIES.forEach(name -> collectForgeRegistry(registries, new ResourceLocation("minecraft", name)));
        ENDERIO_REGISTRIES.forEach(name -> collectForgeRegistry(registries, new ResourceLocation("enderio", name)));
        server.registryAccess().registries().forEach(entry ->
            registries.put(entry.key().location().toString(), enderIOIds(entry.value().keySet().stream())));

        var tags = new TreeMap<String, Map<String, List<String>>>();
        var taggedRegistries = new TreeMap<String, List<String>>();
        collectTags(tags, taggedRegistries, BuiltInRegistries.BLOCK);
        collectTags(tags, taggedRegistries, BuiltInRegistries.ITEM);
        collectTags(tags, taggedRegistries, BuiltInRegistries.FLUID);
        collectTags(tags, taggedRegistries, BuiltInRegistries.ENTITY_TYPE);
        server.registryAccess().registries().forEach(entry -> collectTags(tags, taggedRegistries, entry.value()));

        var snapshot = new TreeMap<String, Object>();
        snapshot.put("mods", ModList.get().getMods().stream().map(IModInfo::getModId).sorted().toList());
        snapshot.put("features", FeatureFlags.REGISTRY.toNames(flags).stream().map(ResourceLocation::toString).sorted().toList());
        snapshot.put("packs", server.getPackRepository().getSelectedIds().stream().sorted().toList());
        snapshot.put("recipes", recipes);
        snapshot.put("sagRecipes", sagRecipes);
        snapshot.put("smeltingRecipes", server.getRecipeManager().getAllRecipesFor(RecipeType.SMELTING).stream()
            .filter(recipe -> !recipe.getResultItem(server.registryAccess()).isEmpty() && recipe.getIngredients().size() == 1
                && !recipe.getIngredients().get(0).isEmpty())
            .map(recipe -> recipe.getId().toString()).sorted().toList());
        snapshot.put("registries", registries);
        snapshot.put("tags", tags);
        snapshot.put("tagRegistryIds", taggedRegistries);
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var dataMaps = new TreeMap<String, Map<String, JsonElement>>();
        DataMaps.types().forEach(map -> collectDataMap(dataMaps, map, server.getRecipeManager(), ops));
        snapshot.put("dataMaps", dataMaps);
        snapshot.put("soulData", Map.of(
            EngineSoul.NAME, encode(EngineSoul.ENGINE.map, EngineSoul.CODEC, ops),
            FarmSoul.NAME, encode(FarmSoul.FARM.map, FarmSoul.CODEC, ops),
            SolarSoul.NAME, encode(SolarSoul.SOLAR.map, SolarSoul.CODEC, ops),
            SpawnerSoul.NAME, encode(SpawnerSoul.SPAWNER.map, SpawnerSoul.CODEC, ops)));
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

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static void collectForgeRegistry(Map<String, List<String>> registries, ResourceLocation key) {
        var registry = RegistryManager.ACTIVE.getRegistry(key);
        if (registry != null) registries.put(key.toString(), enderIOIds(registry.getKeys().stream()));
    }

    private static <V> void collectDataMap(Map<String, Map<String, JsonElement>> snapshot, RegistryDataMap<?, V> map,
            RecipeManager owner, DynamicOps<JsonElement> ops) {
        snapshot.put(map.file().toString(), encode(map.values(owner), map.codec(), ops));
    }

    private static <V> Map<String, JsonElement> encode(Map<ResourceLocation, V> values, Codec<V> codec, DynamicOps<JsonElement> ops) {
        var output = new TreeMap<String, JsonElement>();
        values.forEach((id, value) -> output.put(id.toString(), codec.encodeStart(ops, value)
            .getOrThrow(false, error -> {})));
        return output;
    }

    private static <T> void collectTags(Map<String, Map<String, List<String>>> tags,
            Map<String, List<String>> taggedRegistries, Registry<T> registry) {
        var entries = new TreeMap<String, List<String>>();
        registry.getTags().forEach(tag -> entries.put(tag.getFirst().location().toString(), tag.getSecond().stream()
            .map(holder -> registry.getKey(holder.value()).toString()).sorted().toList()));
        var path = registry.key().location().getPath();
        if (!entries.isEmpty()) tags.put(path, entries);
        taggedRegistries.put(path, registry.keySet().stream().map(ResourceLocation::toString).sorted().toList());
    }

    private static List<String> enderIOIds(Stream<ResourceLocation> ids) {
        return ids.filter(id -> id.getNamespace().equals("enderio")).map(ResourceLocation::toString).sorted().toList();
    }
}
