package com.enderio.core.gametest;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.recipes.CraftingRecipeInput;
import com.enderio.core.common.recipes.RecipeCache;
import com.enderio.core.common.recipes.RecipeIngredientIndex;
import com.enderio.core.common.recipes.RecipeLookup;
import com.enderio.core.common.recipes.RecipeRegistry;
import com.enderio.core.common.recipes.SizedIngredient;
import com.enderio.core.common.serialization.CodecIngredientSerializer;
import com.enderio.enderio.content.machines.alloy.AlloySmeltingRecipe;
import com.enderio.enderio.content.machines.alloy.SmeltingRecipeInheritance;
import com.enderio.enderio.content.machines.slicer.SlicerRecipeManager;
import com.enderio.enderio.content.machines.slicer.SlicingRecipe;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class RecipeReloadTests {
    private static ResourceLocation id(String path) { return ItemDataTestContent.id(path); }
    private static RecipeType<AlloySmeltingRecipe> alloyType() {
        return RecipeRegistry.type(ResourceLocation.fromNamespaceAndPath("enderio", "alloy_smelting"));
    }
    private static AlloySmeltingRecipe alloy(Item item) {
        return new AlloySmeltingRecipe(List.of(SizedIngredient.of(item, 1)), new ItemStack(Items.DIAMOND), 500, 0).withId(id("replaceable"));
    }
    private static RecipeManager manager() { return new RecipeManager(ICondition.IContext.EMPTY); }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void recipeCacheTracksReplacementsRemovalsAndIndependentManagers(GameTestHelper helper) {
        var builds = new AtomicInteger();
        var cache = new RecipeCache<AlloySmeltingRecipe.Input, AlloySmeltingRecipe, List<AlloySmeltingRecipe>>(
            RecipeReloadTests::alloyType, recipes -> { builds.incrementAndGet(); return List.copyOf(recipes); });
        var first = manager();
        var second = manager();
        helper.assertTrue(cache.get(first).isEmpty(), "Initial empty manager was not readable");
        var iron = alloy(Items.IRON_INGOT);
        first.replaceRecipes(List.of(iron));
        helper.assertTrue(cache.get(first).get(0) == iron && cache.get(first) == cache.get(first) && builds.get() == 2,
            "Native replacement or unchanged snapshot reuse failed");
        var gold = alloy(Items.GOLD_INGOT);
        second.replaceRecipes(List.of(gold));
        helper.assertTrue(cache.get(second).get(0) == gold && cache.get(first).get(0) == iron, "Managers shared cached recipes");
        first.replaceRecipes(List.of(gold));
        helper.assertTrue(cache.get(first).get(0) == gold, "Same-ID replacement retained old recipe");
        first.replaceRecipes(List.of());
        helper.assertTrue(cache.get(first).isEmpty() && cache.get(second).get(0) == gold, "Recipe removal or manager isolation failed");
        helper.assertTrue(builds.get() == 5, "Unchanged snapshots caused unnecessary cache rebuilds");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void partialInputsUseDistinctIngredientsAndCustomPredicates(GameTestHelper helper) {
        var iron = new ItemStack(Items.IRON_INGOT);
        var gold = new ItemStack(Items.GOLD_INGOT);
        var index = new RecipeIngredientIndex(List.of(List.of(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), Ingredient.of(Items.IRON_INGOT))));
        helper.assertTrue(index.hasRecipe(List.of(iron, gold)) && index.hasRecipe(List.of(gold, iron)), "Greedy ingredient assignment rejected a valid permutation");
        helper.assertTrue(index.hasRecipe(List.of(gold, ItemStack.EMPTY)), "Partial input rejected a missing ingredient");
        helper.assertTrue(!index.hasRecipe(List.of(gold, gold)) && !index.hasRecipe(List.of(iron, iron, iron)), "Duplicate inputs reused one ingredient slot");
        var custom = new SerializationTestContent.LabelIngredient("selected");
        var customIndex = new RecipeIngredientIndex(List.of(List.of(Ingredient.of(Items.STONE), custom)));
        helper.assertTrue(customIndex.hasRecipe(List.of(custom.getItems()[0], new ItemStack(Items.STONE))), "NBT ingredient could not reassign a broad match");
        helper.assertTrue(!customIndex.hasRecipe(List.of(new ItemStack(Items.STONE), new ItemStack(Items.STONE))), "Custom NBT predicate was flattened to an item match");
        var hiddenMatch = new RecipeIngredientIndex(List.of(List.of(UnlistedIngredient.INSTANCE)));
        helper.assertTrue(hiddenMatch.hasRecipe(List.of(gold)) && !hiddenMatch.hasRecipe(List.of(new ItemStack(Items.DIRT))),
            "Custom display stacks were treated as exhaustive matching data");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void itemTagUpdatesRebuildIngredientIndexesWithoutRecipeReplacement(GameTestHelper helper) {
        var tag = TagKey.create(Registries.ITEM, id("cached_input"));
        var original = new HashMap<TagKey<Item>, List<Holder<Item>>>();
        BuiltInRegistries.ITEM.getTags().forEach(pair -> original.put(pair.getFirst(), pair.getSecond().stream().toList()));
        var changed = new HashMap<>(original);
        var manager = manager();
        var recipe = new AlloySmeltingRecipe(List.of(new SizedIngredient(Ingredient.of(tag), 1)), new ItemStack(Items.DIAMOND), 500, 0).withId(id("tagged"));
        manager.replaceRecipes(List.of(recipe));
        var cache = new RecipeCache<AlloySmeltingRecipe.Input, AlloySmeltingRecipe, RecipeIngredientIndex>(RecipeReloadTests::alloyType,
            recipes -> new RecipeIngredientIndex(recipes.stream().map(AlloySmeltingRecipe::getIngredients).toList()));
        try {
            changed.put(tag, List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.IRON_INGOT)));
            BuiltInRegistries.ITEM.bindTags(changed);
            publishTags(helper);
            var before = cache.get(manager);
            helper.assertTrue(before.hasRecipe(List.of(new ItemStack(Items.IRON_INGOT))), "Initial item tag was not indexed");
            changed.put(tag, List.of(BuiltInRegistries.ITEM.wrapAsHolder(Items.GOLD_INGOT)));
            BuiltInRegistries.ITEM.bindTags(changed);
            publishTags(helper);
            var after = cache.get(manager);
            helper.assertTrue(after != before, "Native tag event did not rebuild the index");
            helper.assertTrue(after.hasRecipe(List.of(new ItemStack(Items.GOLD_INGOT))), "Rebuilt index omitted new tag members");
            helper.assertTrue(!after.hasRecipe(List.of(new ItemStack(Items.IRON_INGOT))), "Rebuilt index retained old tag members");
        } finally {
            original.putIfAbsent(tag, List.of());
            BuiltInRegistries.ITEM.bindTags(original);
            publishTags(helper);
        }
        helper.succeed();
    }

    private static void publishTags(GameTestHelper helper) {
        Ingredient.invalidateAll();
        MinecraftForge.EVENT_BUS.post(new TagsUpdatedEvent(helper.getLevel().registryAccess(), false, false));
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void slicerUsesCurrentManagerPositionsAndCustomPredicates(GameTestHelper helper) {
        var first = manager();
        var second = manager();
        var custom = new SerializationTestContent.LabelIngredient("slicer");
        var recipe = new SlicingRecipe(new ItemStack(Items.DIAMOND), List.of(custom, Ingredient.EMPTY,
            Ingredient.of(Items.GOLD_INGOT), Ingredient.EMPTY, Ingredient.EMPTY, Ingredient.EMPTY), 500).withId(id("slicer_cache"));
        first.replaceRecipes(List.of(recipe));
        var stack = custom.getItems()[0];
        helper.assertTrue(SlicerRecipeManager.isSlicerValid(first, stack, 0) && !SlicerRecipeManager.isSlicerValid(first, stack, 1)
            && !SlicerRecipeManager.isSlicerValid(first, new ItemStack(Items.STONE), 0), "Actual slicer lost position or NBT matching");
        helper.assertTrue(!SlicerRecipeManager.isSlicerValid(second, stack, 0), "Actual slicer reused another manager's recipes");
        first.replaceRecipes(List.of());
        helper.assertTrue(!SlicerRecipeManager.isSlicerValid(first, stack, 0), "Actual slicer retained removed recipes");
        helper.assertTrue(!SlicerRecipeManager.isSlicerValid(first, stack, -1) && !SlicerRecipeManager.isSlicerValid(first, stack, 6), "Invalid slicer slots accepted");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void inheritedSmeltingKeepsIdsOutputsEnergyAndNativeSync(GameTestHelper helper) {
        var manager = new ReloadableManager();
        var original = id("native_smelting");
        manager.load(Map.of(original, smelting("minecraft:iron_ingot")), helper.getLevel().getServer().getResourceManager());
        SmeltingRecipeInheritance.inherit(manager, 1234);
        var derivedId = SmeltingRecipeInheritance.inheritedId(original);
        var inherited = (AlloySmeltingRecipe) manager.byKey(derivedId).orElseThrow();
        helper.assertTrue(derivedId.toString().equals("enderio:smelting/endercore_tests/native_smelting") && inherited.isSmelting()
            && inherited.energy() == 1234 && inherited.experience() == 0.7f && inherited.output().is(Items.IRON_INGOT), "Inherited recipe lost native data");
        inherited.output().setCount(17);
        helper.assertTrue(manager.byKey(original).orElseThrow().getResultItem(helper.getLevel().registryAccess()).getCount() == 1,
            "Derived output aliases the vanilla output");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ClientboundUpdateRecipesPacket(manager.getRecipes()).write(buffer);
            var received = manager();
            received.replaceRecipes(new ClientboundUpdateRecipesPacket(buffer).getRecipes());
            var copy = (AlloySmeltingRecipe) received.byKey(derivedId).orElseThrow();
            helper.assertTrue(buffer.readableBytes() == 0 && copy.output().getCount() == 17 && copy.energy() == 1234 && copy.isSmelting(), "Inherited recipes failed native sync");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeReloadRespectsConditionsRemovalAndExplicitSmeltingOverrides(GameTestHelper helper) {
        var manager = new ReloadableManager();
        var resources = helper.getLevel().getServer().getResourceManager();
        var original = id("reload_smelting");
        var rejected = smelting("minecraft:gold_ingot").getAsJsonObject();
        rejected.add("conditions", JsonParser.parseString("[{\"type\":\"forge:false\"}]"));
        manager.load(Map.of(original, smelting("minecraft:iron_ingot"), id("disabled"), rejected), resources);
        SmeltingRecipeInheritance.inherit(manager, 500);
        helper.assertTrue(manager.getAllRecipesFor(alloyType()).size() == 1 && manager.byKey(id("disabled")).isEmpty(), "Forge-rejected recipe was inherited");
        manager.load(Map.of(original, smelting("minecraft:gold_ingot")), resources);
        SmeltingRecipeInheritance.inherit(manager, 700);
        var replacement = (AlloySmeltingRecipe) manager.byKey(SmeltingRecipeInheritance.inheritedId(original)).orElseThrow();
        helper.assertTrue(replacement.output().is(Items.GOLD_INGOT) && replacement.energy() == 700, "Reload retained old inherited recipe");
        var explicit = alloy(Items.DIAMOND).withId(replacement.getId());
        manager.replaceRecipes(List.of(manager.byKey(original).orElseThrow(), explicit));
        SmeltingRecipeInheritance.inherit(manager, 900);
        helper.assertTrue(manager.byKey(explicit.getId()).orElseThrow() == explicit, "Inheritance overwrote an explicit recipe ID");
        manager.load(Map.of(), resources);
        SmeltingRecipeInheritance.inherit(manager, 700);
        helper.assertTrue(manager.getRecipes().isEmpty(), "Removed furnace recipe left an inherited recipe behind");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void savedRecipeLookupRejectsMissingMalformedAndWrongTypeIds(GameTestHelper helper) {
        var manager = manager();
        var recipe = alloy(Items.IRON_INGOT);
        manager.replaceRecipes(List.of(recipe));
        helper.assertTrue(RecipeLookup.find(manager, alloyType(), recipe.getId().toString()).orElseThrow() == recipe, "Saved native recipe ID did not resolve");
        helper.assertTrue(RecipeLookup.find(manager, RecipeType.SMELTING, recipe.getId().toString()).isEmpty(), "Saved ID cast a different recipe type");
        helper.assertTrue(RecipeLookup.find(manager, alloyType(), "NOT A RESOURCE ID").isEmpty()
            && RecipeLookup.find(manager, alloyType(), "endercore_tests:missing").isEmpty(), "Malformed or missing saved ID accepted");
        manager.replaceRecipes(List.of());
        helper.assertTrue(RecipeLookup.find(manager, alloyType(), recipe.getId().toString()).isEmpty(), "Lookup retained a removed saved recipe");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void craftingSnapshotsSupportNativeShapeAndRemaindersWithoutInventoryMutation(GameTestHelper helper) {
        var ingredients = NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STONE));
        var shaped = new ShapedRecipe(id("offset_shape"), "", CraftingBookCategory.MISC, 1, 1, ingredients, new ItemStack(Items.DIAMOND));
        var source = new ItemStack(Items.STONE, 5);
        var slots = NonNullList.withSize(9, ItemStack.EMPTY);
        slots.set(8, source);
        var input = new CraftingRecipeInput(3, 3, slots);
        helper.assertTrue(shaped.matches(input, helper.getLevel()) && shaped.assemble(input, helper.getLevel().registryAccess()).is(Items.DIAMOND), "Native shaped recipe lost grid offset");
        input.getItem(8).shrink(1);
        helper.assertTrue(source.getCount() == 5 && input.getWidth() == 3 && input.getHeight() == 3, "Crafting snapshot mutated source inventory");
        var milk = new ShapelessRecipe(id("remainder"), "", CraftingBookCategory.MISC, new ItemStack(Items.CAKE),
            NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.MILK_BUCKET)));
        var milkInput = new CraftingRecipeInput(1, 1, List.of(new ItemStack(Items.MILK_BUCKET)));
        helper.assertTrue(milk.matches(milkInput, helper.getLevel()) && milk.getRemainingItems(milkInput).get(0).is(Items.BUCKET), "Native container remainder was lost");
        try { input.clearContent(); helper.fail("Crafting snapshot accepted a container mutation"); } catch (UnsupportedOperationException expected) { }
        helper.succeed();
    }

    private static JsonElement smelting(String result) {
        return JsonParser.parseString("{\"type\":\"minecraft:smelting\",\"ingredient\":{\"item\":\"minecraft:iron_ore\"},\"result\":\"" + result + "\",\"experience\":0.7,\"cookingtime\":200}");
    }

    private static final class ReloadableManager extends RecipeManager {
        ReloadableManager() { super(ICondition.IContext.EMPTY); }
        void load(Map<ResourceLocation, JsonElement> recipes, ResourceManager resources) {
            apply(recipes, resources, InactiveProfiler.INSTANCE);
        }
    }

    private static final class UnlistedIngredient extends AbstractIngredient {
        static final UnlistedIngredient INSTANCE = new UnlistedIngredient();
        static final CodecIngredientSerializer<UnlistedIngredient> SERIALIZER = new CodecIngredientSerializer<>(Codec.unit(INSTANCE), PacketCodec.unit(INSTANCE));
        @Override public boolean test(ItemStack stack) { return stack.is(Items.IRON_INGOT) || stack.is(Items.GOLD_INGOT); }
        @Override public ItemStack[] getItems() { return new ItemStack[] {new ItemStack(Items.IRON_INGOT)}; }
        @Override public boolean isSimple() { return false; }
        @Override public boolean isEmpty() { return false; }
        @Override public CodecIngredientSerializer<UnlistedIngredient> getSerializer() { return SERIALIZER; }
        @Override public JsonElement toJson() { return SERIALIZER.toJson(this); }
    }
}
