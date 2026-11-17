package com.enderio.core.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class IngredientSerializationTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeForgeIngredientDispatchRetainsCustomMatching(GameTestHelper helper) {
        var source = new SerializationTestContent.LabelIngredient("a");
        Ingredient json = Ingredient.fromJson(source.toJson());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            json.toNetwork(buffer);
            Ingredient packet = Ingredient.fromNetwork(buffer);
            for (var ingredient : new Ingredient[] {source, json, packet}) {
                helper.assertTrue(ingredient instanceof SerializationTestContent.LabelIngredient && !ingredient.isEmpty() && !ingredient.isSimple(),
                    "Forge dispatch flattened or emptied custom ingredient");
                helper.assertTrue(ingredient.test(source.getItems()[0]) && !ingredient.test(new ItemStack(Items.STONE)) && !ingredient.test(null),
                    "Custom matching semantics changed");
                helper.assertTrue(ingredient.getStackingIds().size() == 1, "Forge recipe matching lost display item IDs");
            }
            helper.assertTrue(buffer.readableBytes() == 0, "Trailing recipe packet bytes");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeIngredientDispatchRejectsMissingFieldsAndUnknownTypes(GameTestHelper helper) {
        var missing = new JsonObject();
        missing.addProperty("type", "endercore_tests:label_ingredient");
        try { Ingredient.fromJson(missing); helper.fail("Missing custom data accepted"); } catch (JsonSyntaxException expected) { }
        missing.addProperty("type", "endercore_tests:unknown_ingredient");
        try { Ingredient.fromJson(missing); helper.fail("Unknown custom type accepted"); } catch (JsonSyntaxException expected) { }
        helper.succeed();
    }
}
