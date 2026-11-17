package com.enderio.core.gametest;

import com.enderio.core.common.recipes.FluidIngredient;
import com.enderio.core.common.recipes.IngredientCodecs;
import com.enderio.core.common.recipes.SizedFluidIngredient;
import com.enderio.core.common.recipes.SizedIngredient;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.HashMap;
import java.util.List;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class SizedIngredientTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void sizedItemsPreserveAlternativesCustomMatchingAndLargeCounts(GameTestHelper helper) {
        var custom = new SerializationTestContent.LabelIngredient("counted");
        var alternatives = Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT);
        for (var value : List.of(new SizedIngredient(custom, 256), new SizedIngredient(alternatives, 128))) {
            var json = SizedIngredient.FLAT_CODEC.encodeStart(JsonOps.INSTANCE, value).result().orElseThrow();
            var loaded = SizedIngredient.FLAT_CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                SizedIngredient.STREAM_CODEC.encode(buffer, loaded);
                var received = SizedIngredient.STREAM_CODEC.decode(buffer);
                helper.assertTrue(buffer.readableBytes() == 0 && received.count() == value.count(), "Sized ingredient packet truncated count");
                for (ItemStack match : value.getItems()) {
                    helper.assertTrue(received.test(match) && !received.test(match.copyWithCount(value.count() - 1)), "Sized matching ignored quantity or alternative");
                }
                helper.assertTrue(!received.test(new ItemStack(Items.DIRT, 9999)), "Unrelated item accepted");
                if (value.ingredient() == custom) helper.assertTrue(!received.test(new ItemStack(Items.STONE, 9999)), "Custom ingredient NBT predicate was flattened");
            } finally { buffer.release(); }
        }
        var legacy = JsonParser.parseString("""
            {"type":"neoforge:compound","ingredients":[{"item":"minecraft:iron_ingot"},{"item":"minecraft:gold_ingot"}],"count":2}
            """);
        var loaded = SizedIngredient.FLAT_CODEC.parse(JsonOps.INSTANCE, legacy).result().orElseThrow();
        helper.assertTrue(loaded.test(new ItemStack(Items.IRON_INGOT, 2)) && loaded.test(new ItemStack(Items.GOLD_INGOT, 2)), "Baseline compound ingredient form changed");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void sizedFluidTagsObserveNativeTagReloadAndRetainPacketIdentity(GameTestHelper helper) {
        var tag = TagKey.create(Registries.FLUID, ItemDataTestContent.id("recipe_fluid"));
        var original = new HashMap<TagKey<Fluid>, List<Holder<Fluid>>>();
        BuiltInRegistries.FLUID.getTags().forEach(pair -> original.put(pair.getFirst(), pair.getSecond().stream().toList()));
        var changed = new HashMap<>(original);
        var ingredient = SizedFluidIngredient.of(tag, 1500);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            changed.put(tag, List.of(BuiltInRegistries.FLUID.wrapAsHolder(Fluids.WATER)));
            BuiltInRegistries.FLUID.bindTags(changed);
            helper.assertTrue(ingredient.test(new FluidStack(Fluids.WATER, 1500)) && !ingredient.test(new FluidStack(Fluids.WATER, 1499)), "Fluid tag amount changed");
            helper.assertTrue(ingredient.getFluids().length == 1 && ingredient.getFluids()[0].getFluid() == Fluids.WATER, "Initial tag display wrong");
            SizedFluidIngredient.STREAM_CODEC.encode(buffer, ingredient);
            var received = SizedFluidIngredient.STREAM_CODEC.decode(buffer);
            changed.put(tag, List.of(BuiltInRegistries.FLUID.wrapAsHolder(Fluids.LAVA)));
            BuiltInRegistries.FLUID.bindTags(changed);
            for (var value : List.of(ingredient, received)) {
                helper.assertTrue(!value.test(new FluidStack(Fluids.WATER, 2000)) && value.test(new FluidStack(Fluids.LAVA, 1500)), "Fluid predicate cached old tag members");
                helper.assertTrue(value.getFluids().length == 1 && value.getFluids()[0].getFluid() == Fluids.LAVA && value.getFluids()[0].getAmount() == 1500,
                    "Fluid display cached stale members/amount");
            }
        } finally {
            original.putIfAbsent(tag, List.of());
            BuiltInRegistries.FLUID.bindTags(original);
            buffer.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void compoundFluidPredicatesRetainUnionIntersectionAndDifference(GameTestHelper helper) {
        var water = FluidIngredient.of(Fluids.WATER);
        var lava = FluidIngredient.of(Fluids.LAVA);
        var union = FluidIngredient.anyOf(water, lava);
        var intersection = FluidIngredient.allOf(union, water);
        var difference = FluidIngredient.difference(union, lava);
        for (var value : List.of(union, intersection, difference)) {
            var sized = new SizedFluidIngredient(value, 2000);
            var json = SizedFluidIngredient.FLAT_CODEC.encodeStart(JsonOps.INSTANCE, sized).result().orElseThrow();
            var loaded = SizedFluidIngredient.FLAT_CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
            helper.assertTrue(loaded.equals(sized) && loaded.test(new FluidStack(Fluids.WATER, 2000)) && !loaded.test(FluidStack.EMPTY), "Compound predicate JSON changed");
            helper.assertTrue(loaded.test(new FluidStack(Fluids.LAVA, 2000)) == (value == union), "Intersection/difference matching changed");
            var display = loaded.getFluids();
            helper.assertTrue(display.length == (value == union ? 2 : 1), "Compound predicate display changed");
            display[0].setAmount(1);
            helper.assertTrue(loaded.getFluids()[0].getAmount() == 2000, "Display mutation changed future recipe stacks");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void invalidSizedIngredientsAndUnknownFluidTypesFailExplicitly(GameTestHelper helper) {
        for (String json : List.of("{\"item\":\"minecraft:stone\",\"count\":0}", "{\"item\":\"minecraft:stone\",\"count\":-1}",
            "{\"item\":\"endercore_tests:missing\",\"count\":1}")) {
            helper.assertTrue(SizedIngredient.FLAT_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().isEmpty(), "Invalid item ingredient accepted: " + json);
        }
        helper.assertTrue(IngredientCodecs.REQUIRED.parse(JsonOps.INSTANCE, JsonParser.parseString("[]")).result().isEmpty(), "Empty required ingredient accepted");
        for (String json : List.of("{\"fluid\":\"minecraft:water\",\"amount\":0}", "{\"fluid\":\"endercore_tests:missing\"}",
            "{\"fluid\":\"minecraft:water\",\"tag\":\"minecraft:water\"}", "{\"type\":\"endercore_tests:unsupported\",\"fluid\":\"minecraft:water\"}")) {
            helper.assertTrue(SizedFluidIngredient.FLAT_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().isEmpty(), "Invalid fluid ingredient accepted: " + json);
        }
        helper.succeed();
    }
}
