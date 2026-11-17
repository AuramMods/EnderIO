package com.enderio.core.gametest;

import com.enderio.core.common.item.ItemDataKey;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.compat.vanilla.SpawnEggSoulBindable;
import com.enderio.enderio.compat.vanilla.SpawnEggSoulHandler;
import com.enderio.enderio.api.soul.binding.ComponentSoulBindable;
import com.enderio.enderio.api.soul.binding.ReadOnlyComponentSoulBindable;
import com.enderio.enderio.api.soul.storage.SingleComponentSoulHandler;
import com.enderio.enderio.content.filters.item.ItemFilterUtils;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.enderio.core.gametest.ItemDataTestContent.*;

/** Exercises original main-mod classes, compiled into the test output only while the full port is incomplete. */
@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class PortedSoulTests {
    private static final ItemDataKey<Soul> SOUL = new ItemDataKey<>(id("soul"), Soul.OPTIONAL_CODEC, false);
    private static final String NULL_TYPE = "{\"entity_type\":null,\"entity_tag\":{\"Health\":10.0}}";

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void optionalCodecAcceptsEmptyMap(GameTestHelper helper) {
        helper.assertTrue(Soul.OPTIONAL_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).result().orElseThrow().isEmpty(), "Optional empty soul failed");
        helper.assertTrue(Soul.OPTIONAL_CODEC.encodeStart(NbtOps.INSTANCE, Soul.EMPTY).result().orElseThrow().equals(new CompoundTag()), "Empty soul encoding changed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void requiredCodecRejectsEmptyMap(GameTestHelper helper) {
        helper.assertTrue(Soul.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).error().isPresent(), "Required codec accepted empty soul");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void optionalCodecAcceptsLegacyNullType(GameTestHelper helper) {
        helper.assertTrue(Soul.OPTIONAL_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(NULL_TYPE)).result().orElseThrow().isEmpty(), "Optional null entity did not become empty");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void requiredCodecRejectsNullType(GameTestHelper helper) {
        helper.assertTrue(Soul.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(NULL_TYPE)).error().isPresent(), "Required codec accepted null entity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void currentSoulFormatPreservesEntityData(GameTestHelper helper) {
        var soul = Soul.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"entity_type\":\"minecraft:allay\",\"entity_tag\":{\"id\":\"minecraft:allay\",\"Health\":10.0}}")).result().orElseThrow();
        helper.assertTrue(soul.entityType() == EntityType.ALLAY && soul.entityTag().getDouble("Health") == 10, "Current soul data changed");
        helper.assertTrue(!soul.entityTag().contains("id"), "Entity ID was duplicated in data");
        var reloaded = Soul.parse(helper.getLevel().registryAccess(), soul.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertTrue(Soul.isSameEntitySameTag(soul, reloaded), "Native soul save/reload failed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void legacySoulFormatPreservesEntityData(GameTestHelper helper) {
        var soul = Soul.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"entityTag\":{\"id\":\"minecraft:allay\",\"Health\":10.0}}")).result().orElseThrow();
        helper.assertTrue(soul.entityType() == EntityType.ALLAY && soul.entityTag().getDouble("Health") == 10, "Legacy soul data changed");
        helper.assertTrue(Soul.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"entityTag\":{\"id\":\"missing:entity\"}}")).error().isPresent(), "Unknown legacy entity silently became a different entity");
        helper.assertTrue(Soul.OPTIONAL_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"entity_type\":\"missing:entity\",\"entity_tag\":{}}")).error().isPresent(),
            "Unknown present entity was silently treated as an empty optional soul");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void soulHandlersRespectSimulationCopiesAndBindingRules(GameTestHelper helper) {
        var stack = ITEM.toStack();
        var handler = new SingleComponentSoulHandler(stack, SOUL);
        var binder = new ComponentSoulBindable(stack, SOUL);
        var readOnly = new ReadOnlyComponentSoulBindable(stack, SOUL);
        var soul = Soul.of(EntityType.ALLAY);
        soul.entityTag().putFloat("Health", 9);
        helper.assertTrue(handler.tryInsertSoul(soul, true) && !stack.hasTag(), "Simulated soul insertion changed data");
        helper.assertTrue(handler.tryInsertSoul(soul, false) && binder.getBoundSoul().entityType() == EntityType.ALLAY, "Soul views disagree after binding");
        soul.entityTag().putFloat("Health", 1);
        helper.assertTrue(handler.getSoulInSlot(0).entityTag().getFloat("Health") == 9, "Stored soul shares caller data");
        helper.assertTrue(!handler.tryInsertSoul(Soul.of(EntityType.PIG), false), "Filled soul slot accepted replacement");
        helper.assertTrue(handler.tryExtractSoul(true).entityType() == EntityType.ALLAY && readOnly.hasSoul(), "Simulated extraction changed state");
        var copied = ItemStack.of(stack.copy().save(new CompoundTag()));
        handler.tryExtractSoul(false);
        helper.assertTrue(!readOnly.hasSoul() && SOUL.get(copied).entityType() == EntityType.ALLAY, "Extraction lost copied/saved soul");
        boolean denied = false;
        try { readOnly.bindSoul(Soul.of(EntityType.PIG)); } catch (UnsupportedOperationException expected) { denied = true; }
        helper.assertTrue(denied, "Read-only soul binding was writable");
        boolean invalidSlot = false;
        try { handler.getSoulInSlot(1); } catch (IllegalArgumentException expected) { invalidSlot = true; }
        helper.assertTrue(invalidSlot, "Invalid soul slot accepted");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void soulPacketRoundTripsAndRejectsUnknownEntityIds(GameTestHelper helper) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var soul = Soul.of(EntityType.ALLAY);
            soul.entityTag().putString("fixture", "kept");
            Soul.STREAM_CODEC.encode(buffer, soul);
            helper.assertTrue(Soul.isSameEntitySameTag(soul, Soul.STREAM_CODEC.decode(buffer)), "Soul packet lost type/NBT");
            Soul.OPTIONAL_STREAM_CODEC.encode(buffer, Soul.EMPTY);
            helper.assertTrue(Soul.OPTIONAL_STREAM_CODEC.decode(buffer).isEmpty(), "Empty soul packet changed");
            buffer.clear();
            buffer.writeVarInt(Integer.MAX_VALUE);
            buffer.writeNbt(new CompoundTag());
            boolean rejected = false;
            try { Soul.STREAM_CODEC.decode(buffer); } catch (DecoderException expected) { rejected = true; }
            helper.assertTrue(rejected, "Unknown soul packet ID accepted");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void spawnEggSoulsReadNativeEntityTagsWithoutMutation(GameTestHelper helper) {
        var egg = new ItemStack(Items.PIG_SPAWN_EGG);
        var binding = new SpawnEggSoulBindable(egg);
        var handler = new SpawnEggSoulHandler(egg);
        helper.assertTrue(binding.getBoundSoul().entityType() == EntityType.PIG, "Plain spawn egg type changed");
        var custom = egg.getOrCreateTagElement("EntityTag");
        custom.putString("id", "minecraft:cow");
        custom.putFloat("Health", 11);
        var before = egg.save(new CompoundTag());
        var soul = handler.getSoulInSlot(0);
        helper.assertTrue(soul.entityType() == EntityType.COW && soul.entityTag().getFloat("Health") == 11, "Spawn egg override/data was lost");
        helper.assertTrue(binding.getBoundSoul().entityType() == EntityType.COW, "Spawn egg binding retained a stale type");
        soul.entityTag().putFloat("Health", 1);
        helper.assertTrue(before.equals(egg.save(new CompoundTag())), "Reading a spawn egg soul mutated the egg");
        helper.assertTrue(!binding.canBind() && !handler.tryInsertSoul(Soul.of(EntityType.ALLAY), false), "Spawn egg became writable");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void itemFilterNbtComparisonIgnoresOnlyDamage(GameTestHelper helper) {
        var first = new ItemStack(Items.DIAMOND_SWORD);
        var second = first.copy();
        second.setDamageValue(40);
        helper.assertTrue(ItemFilterUtils.doComponentsMatch(first, second), "Separate damage mode was overridden by NBT comparison");
        second.getOrCreateTag().putString("custom", "kept");
        helper.assertTrue(!ItemFilterUtils.doComponentsMatch(first, second) && !ItemFilterUtils.doComponentsMatch(second, first), "Additional NBT was ignored");
        first.getOrCreateTag().putString("custom", "kept");
        var before = first.getTag().copy();
        helper.assertTrue(ItemFilterUtils.doComponentsMatch(first, second) && before.equals(first.getTag()), "Matching mutated stored tags");
        helper.succeed();
    }
}
