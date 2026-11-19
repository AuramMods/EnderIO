package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.api.components.GrindingBallData;
import com.enderio.enderio.content.machines.sag_mill.SagMillBlockEntity;
import com.enderio.enderio.content.machines.sag_mill.SagMillingRecipe;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
import com.enderio.enderio.init.EIORecipes;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.gametests.util.FixtureBuilder;

import java.util.List;

// Scenario group: machines.sag_mill
public class SagMillTests {

    @GameTest
    public static void grindingPreservesTaggedOutputsAndRespectsStackLimits(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var recipe = new SagMillingRecipe(Ingredient.of(Items.STONE), List.of(
                SagMillingRecipe.OutputItem.of(taggedCobblestone(60, "first"), 1.0f, false),
                SagMillingRecipe.OutputItem.of(taggedCobblestone(7, "first"), 1.0f, false),
                SagMillingRecipe.OutputItem.of(taggedCobblestone(2, "second"), 1.0f, false)), 20,
                SagMillingRecipe.BonusType.NONE);
            var input = new SagMillingRecipe.Input(new ItemStack(Items.STONE), GrindingBallData.IDENTITY);
            var outputs = recipe.craft(input, helper.getLevel().registryAccess());
            helper.assertValueEqual(outputs.size(), 3, "grinding must produce three stacks");
            helper.assertTrue(ItemStack.matches(outputs.get(0).getItem(), taggedCobblestone(64, "first")),
                "the first stack holds exactly its maximum");
            helper.assertTrue(ItemStack.matches(outputs.get(1).getItem(), taggedCobblestone(3, "first")),
                "remaining matching items stay available in the second stack");
            helper.assertTrue(ItemStack.matches(outputs.get(2).getItem(), taggedCobblestone(2, "second")),
                "items with different tags remain separate");
            helper.startSequence().thenSucceed();
        });
    }

    private static ItemStack taggedCobblestone(int count, String variant) {
        var stack = new ItemStack(Items.COBBLESTONE, count);
        stack.getOrCreateTag().putString("Variant", variant);
        return stack;
    }

    @GameTest
    public static void recipeSyncPreservesResolvedTagOutputs(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var available = SagMillingRecipe.OutputItem.of(ItemTags.LOGS, 193, 0.75f, false);
            var missingTag = TagKey.create(Registries.ITEM, new ResourceLocation("enderio", "test_missing_sag_output"));
            var missing = SagMillingRecipe.OutputItem.of(missingTag, 4, 0.5f, true);
            var fixed = SagMillingRecipe.OutputItem.of(taggedCobblestone(7, "network"), 1.0f, false);
            var recipe = new SagMillingRecipe(Ingredient.of(Items.STONE), List.of(available, missing, fixed), 20,
                SagMillingRecipe.BonusType.NONE);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var codec = SagMillingRecipe.Serializer.STREAM_CODEC;
                codec.encode(buffer, recipe);
                var synced = codec.decode(buffer).outputs();
                helper.assertTrue(available.output().right().isPresent(), "sync keeps the server recipe tag-backed");
                helper.assertTrue(synced.get(0).output().left().isPresent()
                    && ItemStack.matches(synced.get(0).getItemStack(), available.getItemStack()),
                    "sync includes the resolved item and full count");
                helper.assertTrue(!synced.get(1).isPresent() && synced.get(1).output().right().orElseThrow().count() == 0,
                    "an unavailable tag stays empty on the client");
                helper.assertTrue(synced.get(1).isOptional() && synced.get(1).chance() == 0.5f,
                    "empty output preserves optionality and chance");
                helper.assertTrue(ItemStack.matches(synced.get(2).getItemStack(), fixed.getItemStack()),
                    "sync preserves fixed output NBT");
                helper.assertTrue(!buffer.isReadable(), "sync consumes the whole packet");
            } finally {
                buffer.release();
            }
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    public static void forge6ActiveBallUsesHistoricalModifiersAndPreservesUnknownData(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.SAG_MILL.get().defaultBlockState()));
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var mill = helper.getBlockEntity(0, 1, 0, SagMillBlockEntity.class);
            var historical = new GrindingBallData(1.2f, 1.65f, 0.8f, 40000);
            var tag = new CompoundTag();
            tag.putString("GrindingBallId", "enderio:grindingball/copper_alloy_grinding_ball");
            tag.putInt("GrindingBallDamage", 600);
            mill.load(tag);
            helper.assertTrue(mill.getGrindingBallData().equals(historical), "active copper ball keeps its Forge 6 modifiers");
            var converted = mill.saveWithoutMetadata();
            helper.assertValueEqual(converted.getInt("GrindingBallDamage"), 600, "wear survives the migration");
            helper.assertTrue(!converted.contains("GrindingBallId"), "a known ball saves as a native snapshot");
            converted.putString("GrindingBallId", "enderio:grindingball/flint");
            mill.load(converted);
            helper.assertTrue(mill.getGrindingBallData().equals(historical), "the native snapshot takes precedence");

            String unknown = "custom:grindingball/charged_quartz";
            tag.putString("GrindingBallId", unknown);
            mill.load(tag);
            helper.assertTrue(mill.getGrindingBallData().isIdentity(), "an unknown ID does not reuse prior modifiers");
            var stack = new ItemStack(EIOBlocks.SAG_MILL.get());
            mill.saveToItem(stack);
            var fallback = BlockItem.getBlockEntityData(stack);
            helper.assertTrue(fallback != null && fallback.getString("GrindingBallId").equals(unknown)
                && fallback.getInt("GrindingBallDamage") == 600, "the unknown ID and wear survive native item export");
            helper.startSequence().thenSucceed();
        });
    }

    @GameTest
    // Tests that the SAG Mill can process stone to cobblestone.
    public static void testSagMillStoneGrinding(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.SAG_MILL.get().defaultBlockState()));

        int energyToAdd = 10000;
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            helper.startSequence()
                .thenExecute(() -> {
                    // Insert the capacitor and fill with energy.
                    helper.insertIntoContainer(0, 1, 0, EIOItems.OCTADIC_CAPACITOR.get(), 1);
                    helper.provideEnergyExact(0, 1, 0, energyToAdd);
                })
                // Insert cobblestone for grinding
                .thenExecute(() -> helper.insertIntoContainer(0, 1, 0, Items.STONE, 2))
                // Wait for processing
                .thenExecuteAfter(40, () -> {
                    // Verify input was consumed
                    helper.assertContainerHasExactly(0, 1, 0, Items.STONE, 0);

                    // Should get gravel output from cobblestone
                    helper.assertContainerHasExactly(0, 1, 0, Items.COBBLESTONE, 2);

                    // Ensure energy was consumed correctly
                    var input = new SagMillingRecipe.Input(new ItemStack(Items.STONE, 1), GrindingBallData.IDENTITY);
                    var recipe = helper.getLevel().getRecipeManager().getRecipeFor(EIORecipes.SAG_MILLING.type().get(), input, helper.getLevel()).orElseThrow();
                    int expectedEnergy = energyToAdd - recipe.getBaseEnergyCost() * 2;
                    helper.assertEnergyStored(0, 1, 0, expectedEnergy);
                })
                .thenSucceed();
        });
    }

    //TODO can we properly handle conditional outputs?
    @GameTest
    // Tests that the SAG Mill can process copper ore to raw copper.
    public static void testSagMillCopperOreGrinding(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.SAG_MILL.get().defaultBlockState()));

        int energyToAdd = 10000;
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            helper.startSequence()
                .thenExecute(() -> {
                    // Insert the capacitor and fill with energy.
                    helper.insertIntoContainer(0, 1, 0, EIOItems.OCTADIC_CAPACITOR.get(), 1);
                    helper.provideEnergyExact(0, 1, 0, energyToAdd);
                })
                // Insert copper ore for grinding
                .thenExecute(() -> helper.insertIntoContainer(0, 1, 0, Items.COPPER_ORE, 2))
                // Wait for processing
                .thenExecuteAfter(40, () -> {
                    // Verify input was consumed
                    helper.assertContainerHasExactly(0, 1, 0, Items.COPPER_ORE, 0);

                    // Should get raw copper output from copper ore
                    helper.assertContainerHasAtleast(0, 1, 0, Items.RAW_COPPER, 2);

                    // Ensure energy was consumed correctly
                    var input = new SagMillingRecipe.Input(new ItemStack(Items.COPPER_ORE, 1), GrindingBallData.IDENTITY);
                    var recipe = helper.getLevel().getRecipeManager().getRecipeFor(EIORecipes.SAG_MILLING.type().get(), input, helper.getLevel()).orElseThrow();
                    int expectedEnergy = energyToAdd - recipe.getBaseEnergyCost() * 2;
                    helper.assertEnergyStored(0, 1, 0, expectedEnergy);
                })
                .thenSucceed();
        });
    }

    @GameTest
    // Tests that the SAG Mill can process raw copper to copper dust.
    public static void testSagMillRawCopperGrinding(final GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.SAG_MILL.get().defaultBlockState()));

        int energyToAdd = 10000;
        test.onGameTest(EnderGameTestHelper.class, helper -> {
            helper.startSequence()
                .thenExecute(() -> {
                    // Insert the capacitor and fill with energy.
                    helper.insertIntoContainer(0, 1, 0, EIOItems.OCTADIC_CAPACITOR.get(), 1);
                    helper.provideEnergyExact(0, 1, 0, energyToAdd);
                })
                // Insert raw copper for grinding
                .thenExecute(() -> helper.insertIntoContainer(0, 1, 0, Items.RAW_COPPER, 2))
                // Wait for processing
                .thenExecuteAfter(40, () -> {
                    // Verify input was consumed
                    helper.assertContainerHasExactly(0, 1, 0, Items.RAW_COPPER, 0);

                    // Should get powdered copper output from raw copper
                    helper.assertContainerHasAtleast(0, 1, 0, EIOItems.POWDERED_COPPER.asItem(), 2);

                    // Ensure energy was consumed correctly
                    var input = new SagMillingRecipe.Input(new ItemStack(Items.RAW_COPPER, 1), GrindingBallData.IDENTITY);
                    var recipe = helper.getLevel().getRecipeManager().getRecipeFor(EIORecipes.SAG_MILLING.type().get(), input, helper.getLevel()).orElseThrow();
                    int expectedEnergy = energyToAdd - recipe.getBaseEnergyCost() * 2;
                    helper.assertEnergyStored(0, 1, 0, expectedEnergy);
                })
                .thenSucceed();
        });
    }
}

