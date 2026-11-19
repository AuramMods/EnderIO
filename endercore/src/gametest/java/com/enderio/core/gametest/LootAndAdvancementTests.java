package com.enderio.core.gametest;

import com.enderio.core.common.loot.AddTableLootModifier;
import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.enderio.api.capacitor.CapacitorData;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.config.base.BaseConfig;
import com.enderio.enderio.content.broken_spawner.BrokenSpawnerLootModifier;
import com.enderio.enderio.content.capacitors.SetLootCapacitorFunction;
import com.enderio.enderio.content.paint.BlockPaintData;
import com.enderio.enderio.content.paint.CopyPaintFunction;
import com.enderio.enderio.content.paint.PaintingTrigger;
import com.enderio.enderio.init.EIOCriterions;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.storage.loot.Deserializers;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class LootAndAdvancementTests {
    private static final LootItemCondition[] NO_CONDITIONS = new LootItemCondition[0];

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeSpawnerDropsPreserveSoulAndRespectToolBlacklist(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.SPAWNER.defaultBlockState());
        var spawner = (SpawnerBlockEntity) helper.getLevel().getBlockEntity(pos);
        spawner.setEntityId(EntityType.COW, helper.getLevel().random);
        var chance = BaseConfig.COMMON.BLOCKS.BROKEN_SPAWNER_DROP_CHANCE;
        double original = chance.get();
        try {
            chance.set(1.0);
            var drops = spawner.getBlockState().getDrops(blockParams(helper, spawner, new ItemStack(Items.IRON_PICKAXE)));
            helper.assertTrue(drops.size() == 1 && drops.get(0).is(LootTestContent.BROKEN_SPAWNER.get()),
                "Native block loot did not load the production broken-spawner modifier");
            Soul soul = Soul.ITEM_DATA.get(drops.get(0));
            helper.assertTrue(soul != null && soul.entityType() == EntityType.COW && soul.entityTag().isEmpty(),
                "Spawner drop did not preserve its entity type");
            helper.assertTrue(spawner.getBlockState().getDrops(blockParams(helper, spawner,
                new ItemStack(Items.DIAMOND_PICKAXE))).isEmpty(), "Blacklisted tool produced a spawner");
            chance.set(0.0);
            helper.assertTrue(spawner.getBlockState().getDrops(blockParams(helper, spawner,
                new ItemStack(Items.IRON_PICKAXE))).isEmpty(), "Zero drop chance produced a spawner");
        } finally {
            chance.set(original);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void spawnerModifierPreservesExistingLootAndRejectsMissingSouls(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var modifier = new BrokenSpawnerLootModifier(NO_CONDITIONS);
        for (String entityId : new String[] { "", "endercore_tests:unknown_entity", "invalid id!" }) {
            var spawner = new SpawnerBlockEntity(pos, Blocks.SPAWNER.defaultBlockState());
            // SpawnData normalizes malformed IDs; an unknown valid registry ID must still be rejected.
            if (!entityId.isEmpty()) {
                var entity = new CompoundTag();
                entity.putString("id", entityId);
                var spawn = new CompoundTag();
                spawn.put("entity", entity);
                var tag = new CompoundTag();
                tag.put("SpawnData", spawn);
                spawner.load(tag);
            }
            var loot = new ObjectArrayList<ItemStack>();
            var original = new ItemStack(Items.DIAMOND);
            loot.add(original);
            modifier.apply(loot, blockContext(helper, spawner));
            helper.assertTrue(loot.size() == 1 && loot.get(0) == original, "Missing soul changed existing loot: " + entityId);
        }
        var spawner = new SpawnerBlockEntity(pos, Blocks.SPAWNER.defaultBlockState());
        spawner.setLevel(helper.getLevel());
        spawner.setEntityId(EntityType.COW, helper.getLevel().random);
        var never = new BrokenSpawnerLootModifier(new LootItemCondition[] { LootItemRandomChanceCondition.randomChance(0).build() });
        helper.assertTrue(never.apply(new ObjectArrayList<>(), blockContext(helper, spawner)).isEmpty(),
            "Native global loot conditions were bypassed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void capacitorLootRoundTripsNativeJsonAndWritesPersistentData(GameTestHelper helper) {
        Gson gson = Deserializers.createFunctionSerializer().create();
        var function = roundTrip(gson, SetLootCapacitorFunction.setLootCapacitor(ConstantValue.exactly(2)).build());
        var stack = new ItemStack(Items.PAPER);
        stack.getOrCreateTag().putString("unrelated", "retained");
        helper.assertTrue(function.apply(stack, emptyContext(helper)) == stack, "Capacitor loot replaced the output stack");
        var data = CapacitorData.ITEM_DATA.get(ItemStack.of(stack.save(new CompoundTag())));
        helper.assertTrue(data != null && data.base() == 2 && !data.modifiers().isEmpty() && data.modifiers().size() <= 3
            && data.modifiers().values().stream().allMatch(value -> value == 2), "Capacitor ranges/modifiers did not survive native NBT");
        helper.assertTrue(stack.getTag().getString("unrelated").equals("retained"), "Loot erased unrelated item data");
        var never = roundTrip(gson, SetLootCapacitorFunction.setLootCapacitor(ConstantValue.exactly(4))
            .when(LootItemRandomChanceCondition.randomChance(0)).build());
        never.apply(stack, emptyContext(helper));
        helper.assertTrue(CapacitorData.ITEM_DATA.get(stack).equals(data), "Conditional capacitor loot ran when false");
        var uniform = gson.fromJson("{\"function\":\"enderio:set_loot_capacitor\",\"range\":{\"type\":\"minecraft:uniform\",\"min\":1,\"max\":3}}", LootItemFunction.class);
        var uniformResult = roundTrip(gson, uniform).apply(new ItemStack(Items.PAPER), emptyContext(helper));
        float base = CapacitorData.ITEM_DATA.get(uniformResult).base();
        helper.assertTrue(base >= 1 && base <= 3, "Native uniform number provider was lost");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void paintLootCopiesBothFacesAndPreservesConditionalBehavior(GameTestHelper helper) {
        Gson gson = Deserializers.createFunctionSerializer().create();
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, LootTestContent.PAINTED.get().defaultBlockState());
        var painted = (LootTestContent.PaintEntity) helper.getLevel().getBlockEntity(pos);
        painted.setPrimaryPaint(Blocks.DIAMOND_BLOCK);
        painted.setSecondaryPaint(Blocks.GOLD_BLOCK);
        var primary = roundTrip(gson, CopyPaintFunction.copyPrimary().build());
        var secondary = roundTrip(gson, CopyPaintFunction.copySecondary().build());
        var stack = new ItemStack(Items.STONE);
        primary.apply(stack, blockContext(helper, painted));
        helper.assertTrue(BlockPaintData.ITEM_DATA.get(stack).paint() == Blocks.DIAMOND_BLOCK, "Primary paint was not copied");
        secondary.apply(stack, blockContext(helper, painted));
        helper.assertTrue(BlockPaintData.ITEM_DATA.get(ItemStack.of(stack.save(new CompoundTag()))).paint() == Blocks.GOLD_BLOCK,
            "Secondary paint did not persist");
        painted.primary = null;
        primary.apply(stack, blockContext(helper, painted));
        primary.apply(stack, emptyContext(helper));
        painted.primary = Blocks.DIAMOND_BLOCK;
        roundTrip(gson, CopyPaintFunction.copyPrimary().when(LootItemRandomChanceCondition.randomChance(0)).build())
            .apply(stack, blockContext(helper, painted));
        helper.assertTrue(BlockPaintData.ITEM_DATA.get(stack).paint() == Blocks.GOLD_BLOCK,
            "Absent paint/block entity or false condition erased paint");
        for (String value : new String[] { "", ",\"should_copy_primary\":\"true\"" }) {
            boolean rejected = false;
            try { gson.fromJson("{\"function\":\"enderio:copy_paint\"" + value + "}", LootItemFunction.class); }
            catch (JsonSyntaxException expected) { rejected = true; }
            helper.assertTrue(rejected, "Invalid paint selector was silently accepted");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void additionalLootRollsSplitStacksAndRunGlobalModifiersOnce(GameTestHelper helper) {
        LootTestContent.PostMarker.calls = 0;
        var table = helper.getLevel().getServer().getLootData().getLootTable(id("loot_target"));
        var loot = table.getRandomItems(new LootParams.Builder(helper.getLevel())
            .withDynamicDrop(id("oversized_drop"), consumer -> consumer.accept(new ItemStack(Items.APPLE, 130)))
            .create(LootContextParamSets.EMPTY));
        helper.assertTrue(loot.stream().filter(stack -> stack.is(Items.APPLE)).mapToInt(ItemStack::getCount).sum() == 130,
            "Additional native table did not contribute its rolled items");
        helper.assertTrue(loot.stream().allMatch(stack -> stack.getCount() <= stack.getMaxStackSize()), "Additional table returned oversized stacks");
        helper.assertTrue(LootTestContent.PostMarker.calls == 1
            && loot.stream().filter(stack -> stack.is(Items.PAPER)).mapToInt(ItemStack::getCount).sum() == 1,
            "Subtable invoked global modifiers again or changed the queried table ID");
        var original = new ItemStack(Items.DIAMOND);
        var missing = new ObjectArrayList<ItemStack>();
        missing.add(original);
        new AddTableLootModifier(NO_CONDITIONS, id("missing_table")).apply(missing, emptyContext(helper));
        helper.assertTrue(missing.size() == 1 && missing.get(0) == original, "Missing subtable changed existing loot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void generatedPaintingAdvancementsLoadAndAwardThroughNativeListeners(GameTestHelper helper) {
        var trigger = EIOCriterions.PAINTING_TRIGGER.get();
        helper.assertTrue(CriteriaTriggers.<PaintingTrigger.TriggerInstance>getCriterion(PaintingTrigger.ID) == trigger, "Painting trigger was not registered natively");
        var manager = helper.getLevel().getServer().getAdvancements();
        var rich = manager.getAdvancement(EnderIOAPI.rl("adventure/rich"));
        var richer = manager.getAdvancement(EnderIOAPI.rl("adventure/richer"));
        helper.assertTrue(rich != null && richer != null, "Production painting advancement resources failed to load");
        try (var peer = new Peer(helper)) {
            var progress = peer.player.getAdvancements();
            trigger.trigger(peer.player, Blocks.STONE);
            helper.assertTrue(!progress.getOrStartProgress(rich).isDone(), "Wrong paint awarded advancement");
            trigger.trigger(peer.player, Blocks.DIAMOND_BLOCK);
            helper.assertTrue(progress.getOrStartProgress(rich).isDone() && !progress.getOrStartProgress(richer).isDone(),
                "Diamond painting did not award exactly its native criterion");
            trigger.trigger(peer.player, Blocks.NETHERITE_BLOCK);
            helper.assertTrue(progress.getOrStartProgress(richer).isDone(), "Netherite painting criterion was not awarded");
        }
        var context = new DeserializationContext(id("painting_predicate"), helper.getLevel().getServer().getLootData());
        var restrictive = trigger.createInstance(json("{\"player\":{\"type\":\"minecraft:cow\"},\"paint\":\"minecraft:diamond_block\"}"), context);
        var roundTrip = trigger.createInstance(restrictive.serializeToJson(SerializationContext.INSTANCE), context);
        var advancement = Advancement.Builder.advancement().addCriterion("paint", roundTrip).build(id("painting_predicate"));
        try (var peer = new Peer(helper)) {
            trigger.addPlayerListener(peer.player.getAdvancements(), new CriterionTrigger.Listener<>(roundTrip, advancement, "paint"));
            trigger.trigger(peer.player, Blocks.DIAMOND_BLOCK);
            helper.assertTrue(!peer.player.getAdvancements().getOrStartProgress(advancement).isDone(), "Painting player predicate was bypassed");
        }
        boolean rejected = false;
        try { trigger.createInstance(json("{\"paint\":\"endercore_tests:unknown_block\"}"), context); }
        catch (JsonSyntaxException expected) { rejected = true; }
        helper.assertTrue(rejected, "Unknown painting block silently became air");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeGliderCriterionHonorsPredicatesAndListenerRemoval(GameTestHelper helper) {
        var trigger = EIOCriterions.USE_GLIDER.get();
        helper.assertTrue(CriteriaTriggers.<com.enderio.enderio.content.tools.hang_glider.UseGliderTrigger.TriggerInstance>getCriterion(trigger.getId()) == trigger, "Glider trigger was not registered");
        var context = new DeserializationContext(id("glider"), helper.getLevel().getServer().getLootData());
        var instance = trigger.createInstance(json("{}"), context);
        var restricted = trigger.createInstance(json("{\"player\":{\"type\":\"minecraft:cow\"}}"), context);
        restricted = trigger.createInstance(restricted.serializeToJson(SerializationContext.INSTANCE), context);
        var allowed = Advancement.Builder.advancement().addCriterion("use", instance).build(id("glider_allowed"));
        var denied = Advancement.Builder.advancement().addCriterion("use", restricted).build(id("glider_denied"));
        var removed = Advancement.Builder.advancement().addCriterion("use", instance).build(id("glider_removed"));
        try (var peer = new Peer(helper)) {
            var progress = peer.player.getAdvancements();
            trigger.addPlayerListener(progress, new CriterionTrigger.Listener<>(instance, allowed, "use"));
            trigger.addPlayerListener(progress, new CriterionTrigger.Listener<>(restricted, denied, "use"));
            trigger.trigger(peer.player);
            helper.assertTrue(progress.getOrStartProgress(allowed).isDone() && !progress.getOrStartProgress(denied).isDone(),
                "Glider trigger did not preserve its player predicate");
            trigger.addPlayerListener(progress, new CriterionTrigger.Listener<>(instance, removed, "use"));
            trigger.removePlayerListeners(progress);
            trigger.trigger(peer.player);
            helper.assertTrue(!progress.getOrStartProgress(removed).isDone(), "Removed glider listener still awarded progress");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void productionLootAndAdvancementGeneratorsWriteNativeResources(GameTestHelper helper) throws Exception {
        var output = java.nio.file.Files.createTempDirectory("enderio-loot-datagen-");
        try {
            var pack = new net.minecraft.data.PackOutput(output);
            var registries = java.util.concurrent.CompletableFuture.<net.minecraft.core.HolderLookup.Provider>completedFuture(
                helper.getLevel().registryAccess());
            new com.enderio.enderio.datagen.common.loot.EIOLootModifiersProvider(pack, registries)
                .run(net.minecraft.data.CachedOutput.NO_CACHE).join();
            var entries = json(java.nio.file.Files.readString(output.resolve("data/forge/loot_modifiers/global_loot_modifiers.json")));
            var names = new java.util.HashSet<String>();
            entries.getAsJsonArray("entries").forEach(entry -> names.add(entry.getAsString()));
            helper.assertTrue(!entries.get("replace").getAsBoolean() && entries.getAsJsonArray("entries").size() == 4
                && names.equals(java.util.Set.of("enderio:broken_spawner", "enderio:common_loot", "enderio:alloy_loot", "enderio:auto_smelt")),
                "Native global modifier list changed IDs or replace semantics");
            for (String name : new String[] { "broken_spawner", "common_loot", "alloy_loot", "auto_smelt" }) {
                var generated = json(java.nio.file.Files.readString(output.resolve("data/enderio/loot_modifiers/" + name + ".json")));
                try (var reader = helper.getLevel().getServer().getResourceManager()
                        .getResourceOrThrow(EnderIOAPI.rl("loot_modifiers/" + name + ".json")).openAsReader()) {
                    helper.assertTrue(generated.equals(JsonParser.parseReader(reader)), "Production loot JSON differs from native generator: " + name);
                }
                helper.assertTrue(net.minecraftforge.common.loot.IGlobalLootModifier.DIRECT_CODEC
                    .parse(com.mojang.serialization.JsonOps.INSTANCE, generated).error().isEmpty(), "Native generated loot failed to decode");
            }
            var generator = new com.enderio.enderio.datagen.common.advancement.EIOAdvancementGenerator();
            var existing = new net.minecraftforge.common.data.ExistingFileHelper(java.util.List.of(), java.util.Set.of(), true, null, null);
            new net.minecraftforge.common.data.ForgeAdvancementProvider(pack, registries, existing, java.util.List.of(generator))
                .run(net.minecraft.data.CachedOutput.NO_CACHE).join();
            for (String name : new String[] { "rich", "richer" }) {
                var generated = json(java.nio.file.Files.readString(output.resolve("data/enderio/advancements/adventure/" + name + ".json")));
                helper.assertTrue(generated.getAsJsonObject("display").getAsJsonObject("icon").has("item"),
                    "Advancement generator used newer icon syntax");
                var context = new DeserializationContext(EnderIOAPI.rl("adventure/" + name), helper.getLevel().getServer().getLootData());
                var criterion = net.minecraft.advancements.Criterion.criterionFromJson(
                    generated.getAsJsonObject("criteria").getAsJsonObject("paint"), context);
                helper.assertTrue(criterion.getTrigger() instanceof PaintingTrigger.TriggerInstance,
                    "Generated advancement did not decode its native painting criterion");
            }
        } finally {
            try (var files = java.nio.file.Files.walk(output)) {
                for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.delete(file);
            }
        }
        helper.succeed();
    }

    private static LootParams.Builder blockParams(GameTestHelper helper, BlockEntity entity, ItemStack tool) {
        return new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(entity.getBlockPos()))
            .withParameter(LootContextParams.BLOCK_ENTITY, entity).withParameter(LootContextParams.TOOL, tool)
            .withParameter(LootContextParams.BLOCK_STATE, entity.getBlockState());
    }

    private static LootContext blockContext(GameTestHelper helper, BlockEntity entity) {
        return new LootContext.Builder(blockParams(helper, entity, new ItemStack(Items.IRON_PICKAXE)).create(LootContextParamSets.BLOCK))
            .withOptionalRandomSeed(42).create(null);
    }

    private static LootContext emptyContext(GameTestHelper helper) {
        return new LootContext.Builder(new LootParams.Builder(helper.getLevel()).create(LootContextParamSets.EMPTY))
            .withOptionalRandomSeed(42).create(null);
    }

    private static LootItemFunction roundTrip(Gson gson, LootItemFunction function) {
        return gson.fromJson(gson.toJsonTree(function, LootItemFunction.class), LootItemFunction.class);
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static ResourceLocation id(String value) { return ResourceLocation.fromNamespaceAndPath("endercore_tests", value); }

    private static final class Peer implements AutoCloseable {
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        final EmbeddedChannel socket = new EmbeddedChannel(connection);
        final ServerPlayer player;
        Peer(GameTestHelper helper) {
            var level = helper.getLevel();
            player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "LootFixture"));
            new ServerGamePacketListenerImpl(level.getServer(), connection, player);
            connection.setProtocol(ConnectionProtocol.PLAY);
        }
        @Override public void close() {
            player.getAdvancements().stopListening();
            socket.finishAndReleaseAll();
            player.getTextFilter().leave();
        }
    }
}
