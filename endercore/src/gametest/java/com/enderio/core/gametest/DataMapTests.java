package com.enderio.core.gametest;

import com.enderio.core.common.datamap.DataMapProvider;
import com.enderio.core.common.datamap.DataMapReloadListener;
import com.enderio.core.common.datamap.DataMapSync;
import com.enderio.core.common.datamap.DataMaps;
import com.enderio.core.common.datamap.RegistryDataMap;
import com.enderio.core.common.recipes.SizedFluidIngredient;
import com.enderio.enderio.api.components.GrindingBallData;
import com.enderio.enderio.content.machines.vat.FermentingRecipe;
import com.enderio.enderio.foundation.datamap.RangeExtender;
import com.enderio.enderio.foundation.datamap.VatReagent;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class DataMapTests {
    private static final ResourceLocation PAPER = ResourceLocation.parse("minecraft:paper");
    private static final ResourceLocation FLINT = ResourceLocation.parse("minecraft:flint");
    private static final RegistryDataMap<Item, String> PROBE = new RegistryDataMap<>(
        ResourceLocation.parse("endercore_tests:probe"), Registries.ITEM, Codec.STRING, false);
    private static final TagKey<Item> FIRST = TagKey.create(Registries.ITEM, ResourceLocation.parse("endercore_tests:ferment_a"));
    private static final TagKey<Item> SECOND = TagKey.create(Registries.ITEM, ResourceLocation.parse("endercore_tests:ferment_b"));

    static void register() {
        DataMaps.register(PROBE);
        DataMaps.register(GrindingBallData.DATA_MAP_TYPE);
        DataMaps.register(VatReagent.DATA_MAP);
        DataMaps.register(RangeExtender.DATA_MAP);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nativeReloadEventLoadsDataAndActualReagentCrafts(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.assertTrue("native reload".equals(PROBE.get(level, Items.PAPER)), "Native resource reload did not load map");
        helper.assertTrue("staged tag".equals(PROBE.get(level, Items.SUGAR_CANE)), "Native loader resolved tags before they were prepared");
        helper.assertTrue(RangeExtender.DATA_MAP.get(level, Blocks.STONE).get(TagKey.create(Registries.BLOCK,
            ResourceLocation.parse("endercore_tests:range"))) == 16, "Native block map did not load");
        var recipe = new FermentingRecipe(SizedFluidIngredient.of(Fluids.WATER, 1000), FIRST, SECOND,
            new FluidStack(Fluids.WATER, 1000), 20);
        var sugar = new ItemStack(Items.SUGAR_CANE);
        var glowstone = new ItemStack(Items.GLOWSTONE_DUST);
        var first = new FermentingRecipe.Input(sugar, glowstone, new FluidStack(Fluids.WATER, 1000), level);
        var reversed = new FermentingRecipe.Input(glowstone, sugar, new FluidStack(Fluids.WATER, 1000), level);
        helper.assertTrue(recipe.matches(first, level) && recipe.matches(reversed, level), "Staged tags were not loaded");
        helper.assertTrue(recipe.craft(first, level.registryAccess()).get(0).getFluid().getAmount() == 125
            && recipe.craft(reversed, level.registryAccess()).get(0).getFluid().getAmount() == 125,
            "Actual vat recipe did not apply both synchronized reagent multipliers");
        helper.assertTrue(FermentingRecipe.getModifier(level, new ItemStack(Items.DIRT), FIRST) == 1,
            "Missing reagent override lost the default multiplier");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void packStackOverridesTagsRemovalsAndMalformedLayers(GameTestHelper helper) throws Exception {
        var owner = new RecipeManager();
        load(helper, owner, PROBE,
            "{\"values\":{\"#endercore_tests:staged\":\"base\"}}",
            "{\"values\":{\"minecraft:paper\":{\"value\":\"override\",\"replace\":true}},\"remove\":[\"minecraft:flint\"]}",
            "{\"replace\":true,\"values\":{\"minecraft:paper\":\"partial\",\"minecraft:flint\":{\"bad\":1}}}",
            "{\"replace\":\"true\",\"values\":{\"minecraft:paper\":\"bad boolean\"}}");
        helper.assertTrue(PROBE.values(owner).equals(Map.of(PAPER, "override")), "Pack precedence/removal or failed-layer atomicity changed");
        load(helper, owner, PROBE, "{\"values\":{\"minecraft:flint\":\"first\"}}",
            "{\"replace\":true,\"values\":{\"minecraft:paper\":\"replacement\"}}");
        helper.assertTrue(PROBE.values(owner).equals(Map.of(PAPER, "replacement")), "Whole-map replace retained old entries");
        load(helper, owner, PROBE);
        helper.assertTrue(PROBE.values(owner).isEmpty(), "Removing all files retained the previous reload's values");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void conditionsAndAdvancedMapsPreserveSourceReplacementRules(GameTestHelper helper) throws Exception {
        var owner = new RecipeManager();
        load(helper, owner, PROBE, """
            {"values":{
              "minecraft:paper":{"neoforge:conditions":[{"type":"neoforge:true"}],"value":{"value":"condition passed","replace":true}},
              "minecraft:flint":{"forge:conditions":[{"type":"forge:false"}],"value":{"invalid":1}}
            }}
            """);
        helper.assertTrue(PROBE.values(owner).equals(Map.of(PAPER, "condition passed")), "Conditional wrappers decoded incorrectly");
        load(helper, owner, VatReagent.DATA_MAP,
            "{\"values\":{\"minecraft:paper\":{\"#endercore_tests:a\":2,\"#endercore_tests:b\":3}}}",
            "{\"values\":{\"minecraft:paper\":{\"#endercore_tests:c\":4}}}");
        var values = VatReagent.DATA_MAP.values(owner).get(PAPER);
        helper.assertTrue(values.size() == 1 && values.get(TagKey.create(Registries.ITEM,
            ResourceLocation.parse("endercore_tests:c"))) == 4D, "Default merger incorrectly merged inner maps");
        load(helper, owner, VatReagent.DATA_MAP,
            "{\"values\":{\"#endercore_tests:staged\":{\"#endercore_tests:a\":2}}}",
            "{\"values\":{},\"remove\":{\"minecraft:paper\":{}}}",
            "{\"values\":{},\"remove\":[{\"key\":\"minecraft:flint\",\"remover\":{}}]}");
        helper.assertTrue(VatReagent.DATA_MAP.values(owner).isEmpty(), "Advanced default removals failed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void snapshotOwnersAndNativeDatagenRoundTripIndependently(GameTestHelper helper) throws Exception {
        var server = new RecipeManager();
        var client = new RecipeManager();
        load(helper, server, PROBE, "{\"values\":{\"minecraft:paper\":\"server\"}}");
        var output = Files.createTempDirectory("enderio-data-map-output-");
        try {
            new DataMapProvider(new PackOutput(output), CompletableFuture.completedFuture(helper.getLevel().registryAccess())) {
                @Override protected void gather() { builder(PROBE).add(PAPER, "generated", true); }
            }.run(CachedOutput.NO_CACHE).join();
            var file = output.resolve("data").resolve(PROBE.file().getNamespace()).resolve(PROBE.file().getPath());
            helper.assertTrue(JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("values")
                .getAsJsonObject(PAPER.toString()).get("replace").getAsBoolean(), "Datagen lost entry replacement metadata");
            try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                    List.of(new PathPackResources("generated", output, false)))) {
                reload(helper, client, resources, List.of(PROBE));
            }
            helper.assertTrue(PROBE.values(server).equals(Map.of(PAPER, "server"))
                && PROBE.values(client).equals(Map.of(PAPER, "generated")), "Client resource set overwrote server state");
            DataMaps.clear(client);
            helper.assertTrue(PROBE.values(client).isEmpty() && !PROBE.values(server).isEmpty(), "Connection cleanup removed server state");
        } finally { delete(output); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void multiChunkSyncPublishesAtomicallyAndClearsRemovedValues(GameTestHelper helper) {
        var owner = new RecipeManager();
        DataMaps.replace(owner, new DataMaps.Snapshot(Map.of(PROBE, Map.of(PAPER, "previous"))));
        String value = "x".repeat(DataMapSync.CHUNK_BYTES * 2 + 17);
        var source = new DataMaps.Snapshot(Map.of(PROBE, Map.of(PAPER, value)));
        var packets = DataMapSync.packets(source, helper.getLevel().registryAccess());
        helper.assertTrue(packets.size() > 1, "Fixture did not cross native chunk boundary");
        for (int i = 0; i < packets.size(); i++) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                DataMapSync.Chunk.CODEC.encode(buffer, packets.get(i));
                var decoded = DataMapSync.Chunk.CODEC.decode(buffer);
                helper.assertTrue(!buffer.isReadable(), "Chunk codec left trailing data");
                DataMapSync.accept(owner, helper.getLevel().registryAccess(), decoded);
            } finally { buffer.release(); }
            helper.assertTrue(PROBE.values(owner).get(PAPER).equals(i + 1 == packets.size() ? value : "previous"),
                "Partial transfer replaced the live snapshot");
        }
        var empty = DataMapSync.packets(new DataMaps.Snapshot(Map.of()), helper.getLevel().registryAccess());
        empty.forEach(chunk -> DataMapSync.accept(owner, helper.getLevel().registryAccess(), chunk));
        helper.assertTrue(PROBE.values(owner).isEmpty() && source.values(PROBE).get(PAPER).equals(value),
            "Empty synchronization retained removed values or mutated sender snapshot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void rejectedTransfersKeepLastGoodSnapshotAndAllowReconnect(GameTestHelper helper) {
        var owner = new RecipeManager();
        DataMaps.replace(owner, new DataMaps.Snapshot(Map.of(PROBE, Map.of(PAPER, "previous"))));
        var packets = DataMapSync.packets(new DataMaps.Snapshot(Map.of(PROBE,
            Map.of(PAPER, "x".repeat(DataMapSync.CHUNK_BYTES * 2)))), helper.getLevel().registryAccess());
        boolean rejected = false;
        try { DataMapSync.accept(owner, helper.getLevel().registryAccess(), packets.get(1)); }
        catch (RuntimeException expected) { rejected = true; }
        helper.assertTrue(rejected && PROBE.values(owner).get(PAPER).equals("previous"), "Out-of-order packet changed live data");
        var valid = DataMapSync.packets(new DataMaps.Snapshot(Map.of(PROBE, Map.of(PAPER, "valid"))), helper.getLevel().registryAccess()).get(0);
        byte[] corrupt = valid.data();
        corrupt[corrupt.length - 1] = (byte) '\\';
        rejected = false;
        try { DataMapSync.accept(owner, helper.getLevel().registryAccess(), new DataMapSync.Chunk(valid.id(), valid.totalBytes(), 0, corrupt)); }
        catch (RuntimeException expected) { rejected = true; }
        helper.assertTrue(rejected && PROBE.values(owner).get(PAPER).equals("previous"), "Malformed complete snapshot was partially applied");
        DataMaps.clear(owner);
        DataMapSync.accept(owner, helper.getLevel().registryAccess(), valid);
        helper.assertTrue(PROBE.values(owner).equals(Map.of(PAPER, "valid")), "New connection could not accept valid data");
        rejected = false;
        try { new DataMapSync.Chunk(valid.id(), DataMapSync.MAX_BYTES + 1, 0, valid.data()); }
        catch (RuntimeException expected) { rejected = true; }
        helper.assertTrue(rejected, "Oversized transfer header was accepted");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void actualGrindingBallOptionalPersistencePreservesNonIdentityValues(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var ball = new GrindingBallData(1.35F, 2F, 0.7F, 125000);
        var tag = (CompoundTag) ball.saveOptional(registries);
        helper.assertTrue(!tag.isEmpty() && GrindingBallData.parseOptional(registries, tag).equals(ball),
            "Optional save discarded the actual grinding ball");
        helper.assertTrue(((CompoundTag) GrindingBallData.IDENTITY.saveOptional(registries)).isEmpty()
            && GrindingBallData.parseOptional(registries, new CompoundTag()).isIdentity(), "Identity optional save failed");
        boolean rejected = false;
        try { GrindingBallData.IDENTITY.save(registries); }
        catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "Required save silently encoded identity");
        helper.succeed();
    }

    private static void load(GameTestHelper helper, RecipeManager owner, RegistryDataMap<?, ?> type, String... layers) throws Exception {
        var root = Files.createTempDirectory("enderio-data-map-packs-");
        try {
            var packs = new ArrayList<PathPackResources>();
            for (int i = 0; i < layers.length; i++) {
                Path pack = root.resolve("pack" + i);
                Path file = pack.resolve("data").resolve(type.file().getNamespace()).resolve(type.file().getPath());
                Files.createDirectories(file.getParent());
                Files.writeString(file, layers[i]);
                packs.add(new PathPackResources("layer" + i, pack, false));
            }
            try (var resources = new MultiPackResourceManager(PackType.SERVER_DATA, List.copyOf(packs))) {
                reload(helper, owner, resources, List.of(type));
            }
        } finally { delete(root); }
    }

    private static void reload(GameTestHelper helper, RecipeManager owner, MultiPackResourceManager resources,
                               List<RegistryDataMap<?, ?>> types) {
        var access = helper.getLevel().registryAccess();
        var conditions = new ICondition.IContext() {
            @Override public <T> Map<ResourceLocation, Collection<Holder<T>>> getAllTags(ResourceKey<? extends Registry<T>> registry) {
                var values = new LinkedHashMap<ResourceLocation, Collection<Holder<T>>>();
                var actual = access.registryOrThrow(registry);
                actual.getTags().forEach(pair -> values.put(pair.getFirst().location(), pair.getSecond().stream().toList()));
                if (registry.equals(Registries.ITEM)) {
                    values.put(ResourceLocation.parse("endercore_tests:staged"), List.of(actual.getHolderOrThrow(ResourceKey.create(registry, PAPER)),
                        actual.getHolderOrThrow(ResourceKey.create(registry, FLINT))));
                }
                return values;
            }
        };
        var barrier = new PreparableReloadListener.PreparationBarrier() {
            @Override public <T> CompletableFuture<T> wait(T value) { return CompletableFuture.completedFuture(value); }
        };
        new DataMapReloadListener(owner, access, conditions, types).reload(barrier, resources,
            InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, Runnable::run, Runnable::run).join();
    }

    private static void delete(Path root) throws Exception {
        try (var files = Files.walk(root)) {
            for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file);
        }
    }
}
