package com.enderio.port.clientqa;

import com.enderio.enderio.config.base.BaseConfig;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/** Crafts and eats through the packaged integrated server and its vanilla crafting menu. */
final class ClientXpCraftingSmoke {
    private enum Stage { TITLE, WORLD, FIRE, UNLOCK, MENU, RESULT, CEREAL_CURSOR, TAKEN, HUNGRY, EATING, MEAL, CAPTURE, DONE }

    private static final String CEREAL = "enderios";
    private static final String SPECIAL_NAME = "SOIREDNE";
    private static final int SPECIAL_PROFILE_HASH = -1435081874;
    private static final BlockPos TABLE = new BlockPos(0, 80, 0);
    private static final Vec3 TABLE_STAND = new Vec3(0.5, 80, 3.5);
    private static final AABB FIRE_AREA = new AABB(6, 80, -4, 14, 83, 4);
    private static final int MENU_HOTBAR_START = 37;
    private final Minecraft minecraft = Minecraft.getInstance();
    private final boolean special;
    private final List<String> recipes;
    private final long started = System.nanoTime();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final Map<String, Object> result = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private CompletableFuture<?> fixture;
    private CompletableFuture<Crafted> crafted;
    private CompletableFuture<FireDrops> fireDrops;
    private CompletableFuture<Meal> meal;
    private int nextFireCheck;
    private int recipeIndex;
    private int cerealHotbar;
    private int expectedInputs;
    private int resultFrames;
    private int captureFrames;

    ClientXpCraftingSmoke(boolean special) {
        this.special = special;
        recipes = special ? List.of(CEREAL) : List.of("void_vial", "experience_rod", CEREAL);
        result.put("checks", checks);
        result.put("captures", captures);
        result.put("specialProfile", special);
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (System.nanoTime() - started > 170_000_000_000L) throw new AssertionError("Crafting stopped in " + stage);
            var player = minecraft.player;
            var server = minecraft.getSingleplayerServer();
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    var name = System.getProperty("enderio.clientqa.worldName");
                    var settings = new LevelSettings(name, GameType.SURVIVAL, false, Difficulty.NORMAL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT);
                    stage = Stage.WORLD;
                    minecraft.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(88L, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                }
                case WORLD -> {
                    if (player == null || server == null || minecraft.screen != null) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("actual_packaged_survival_world", !player.isCreative() && !player.isSpectator() && connection.isMemoryConnection());
                    check("native_crafting_profile", (player.getUUID().hashCode() == SPECIAL_PROFILE_HASH) == special);
                    fixture = server.submit(() -> prepareWorld(server));
                    stage = special ? Stage.UNLOCK : Stage.FIRE;
                }
                case FIRE -> {
                    if (!fixture.isDone() || player.tickCount < nextFireCheck) return;
                    fixture.join();
                    if (fireDrops == null) {
                        fireDrops = server.submit(() -> collectFireDrops(server));
                    } else if (fireDrops.isDone()) {
                        var drops = fireDrops.join();
                        fireDrops = null;
                        nextFireCheck = player.tickCount + 10;
                        if (drops.seeds() < 3 || drops.grains() < 2) return;
                        check("stock_bedrock_fire_produces_both_ingredients", true);
                        result.put("fireDrops", drops);
                        stage = Stage.UNLOCK;
                    }
                }
                case UNLOCK -> {
                    if (!fixture.isDone() || player.distanceToSqr(TABLE_STAND) > 0.12
                        || !minecraft.level.getBlockState(TABLE).is(Blocks.CRAFTING_TABLE)) return;
                    fixture.join();
                    if (!special && (count("suspicious_seed") < 3 || count("grains_of_infinity") < 2)) return;
                    if (recipes.stream().anyMatch(id -> !player.getRecipeBook().contains(recipe(id)))) return;
                    check("survival_recipe_book_unlocks", true);
                    var hit = new BlockHitResult(Vec3.atCenterOf(TABLE).relative(Direction.SOUTH, 0.5), Direction.SOUTH, TABLE, false);
                    check("open_native_crafting_table", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
                    stage = Stage.MENU;
                }
                case MENU, RESULT, CEREAL_CURSOR, TAKEN -> craft();
                case HUNGRY -> {
                    if (minecraft.screen != null || !crafted.isDone()) return;
                    var actual = crafted.join();
                    if (actual == null) {
                        crafted = server.submit(() -> prepareMeal(server));
                        return;
                    }
                    if (player.getFoodData().getFoodLevel() != 10) return;
                    var cereal = player.getMainHandItem();
                    check("server_crafted_exactly_one_of_each_recipe", actual.craftedOnce());
                    check("native_crafting_consumes_the_stock_inputs", actual.ingredientsConsumed());
                    check("server_inventory_has_the_displayed_cereal", actual.name().equals(cereal.getHoverName().getString())
                        && actual.customName() == special && actual.bucketCount() == 1);
                    result.put("crafted", actual);
                    check("cereal_starts_in_main_hand", player.getMainHandItem().is(EIOItems.ENDERIOS.get()));
                    minecraft.options.keyUse.setDown(true);
                    check("native_survival_eating", minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND).consumesAction()
                        && player.isUsingItem());
                    stage = Stage.EATING;
                }
                case EATING -> {
                    if (player.isUsingItem()) return;
                    minecraft.options.keyUse.setDown(false);
                    if (!player.getMainHandItem().is(Items.BOWL) || player.getFoodData().getFoodLevel() != 20) return;
                    meal = server.submit(() -> {
                        var owner = owner(server);
                        return new Meal(owner.getFoodData().getFoodLevel(), owner.getFoodData().getSaturationLevel(),
                            owner.getMainHandItem().is(Items.BOWL), owner.position(), BaseConfig.COMMON.ITEMS.ENDERIOS_CHANCE.get());
                    });
                    stage = Stage.MEAL;
                }
                case MEAL -> {
                    if (!meal.isDone()) return;
                    var actual = meal.join();
                    check("returns_one_bowl_and_restores_ten_food_and_sixteen_saturation",
                        actual.bowl() && actual.food() == 20 && actual.saturation() == 16.0f && player.getMainHandItem().getCount() == 1);
                    var offset = actual.position().subtract(TABLE_STAND);
                    check("configured_cereal_teleport", special
                        ? actual.chance() == 1.0 && offset.horizontalDistanceSqr() > 0.01 && offset.horizontalDistanceSqr() <= 32
                        : actual.chance() == 0.0 && offset.horizontalDistanceSqr() < 0.01);
                    result.put("meal", actual);
                    minecraft.setScreen(new InventoryScreen(player));
                    stage = Stage.CAPTURE;
                }
                case CAPTURE -> {
                    if (++captureFrames < 6) return;
                    capture("after-eating");
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft() throws Exception {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var player = minecraft.player;
        var menu = screen.getMenu();
        var id = recipes.get(recipeIndex);
        switch (stage) {
            case MENU -> {
                var recipe = recipe(id);
                expectedInputs = (int) recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).count();
                resultFrames = 0;
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                if (!menu.getSlot(0).getItem().is(item(id))
                    || grid(menu).stream().map(Slot::getItem).mapToInt(ItemStack::getCount).sum() != expectedInputs) return;
                if (++resultFrames < 3) return;
                check(id + "_native_result", menu.getSlot(0).getItem().getCount() == 1);
                capture(id + "-recipe");
                if (id.equals(CEREAL)) {
                    click(menu.containerId, 0, ClickType.PICKUP);
                    stage = Stage.CEREAL_CURSOR;
                } else {
                    click(menu.containerId, 0, ClickType.QUICK_MOVE);
                    stage = Stage.TAKEN;
                }
            }
            case CEREAL_CURSOR -> {
                var stack = menu.getCarried();
                if (!stack.is(EIOItems.ENDERIOS.get())
                    || grid(menu).stream().map(Slot::getItem).filter(ItemStack::isEmpty).count() != 8) return;
                check("crafted_cereal_name", special ? stack.hasCustomHoverName() && stack.getHoverName().getString().equals(SPECIAL_NAME)
                    : !stack.hasCustomHoverName());
                var model = minecraft.getItemRenderer().getModel(stack, minecraft.level, player, 42);
                var sprite = model.getParticleIcon().contents().name();
                check("native_crafted_cereal_appearance", sprite.toString().equals(special ? "enderio:item/soiredne" : "enderio:item/enderios"));
                result.put("cerealName", stack.getHoverName().getString());
                check("milk_returns_an_empty_bucket", grid(menu).stream()
                    .map(Slot::getItem).filter(stackInGrid -> stackInGrid.is(Items.BUCKET)).mapToInt(ItemStack::getCount).sum() == 1);
                cerealHotbar = IntStream.range(0, Inventory.getSelectionSize())
                    .filter(slot -> player.getInventory().getItem(slot).isEmpty()).findFirst().orElseThrow();
                click(menu.containerId, MENU_HOTBAR_START + cerealHotbar, ClickType.PICKUP);
                stage = Stage.TAKEN;
            }
            case TAKEN -> {
                if (count(id) != 1 || menu.getSlot(0).hasItem() || !menu.getCarried().isEmpty()) return;
                if (id.equals(CEREAL)) {
                    check("cereal_is_in_the_selected_hotbar_slot",
                        player.getInventory().getItem(cerealHotbar).is(EIOItems.ENDERIOS.get()));
                    player.closeContainer();
                    player.getInventory().selected = cerealHotbar;
                    var server = minecraft.getSingleplayerServer();
                    crafted = server.submit(() -> prepareMeal(server));
                    stage = Stage.HUNGRY;
                } else {
                    if (grid(menu).stream().anyMatch(Slot::hasItem)) return;
                    check(id + "_consumes_all_grid_ingredients", true);
                    recipeIndex++;
                    stage = Stage.MENU;
                }
            }
            default -> { }
        }
    }

    private Crafted prepareMeal(IntegratedServer server) {
        var owner = owner(server);
        var inventory = owner.getInventory();
        var cereal = inventory.getItem(cerealHotbar);
        if (owner.containerMenu != owner.inventoryMenu || !cereal.is(EIOItems.ENDERIOS.get())) return null;
        owner.getFoodData().setFoodLevel(10);
        owner.getFoodData().setSaturation(0.0f);
        boolean craftedOnce = recipes.stream().map(ClientXpCraftingSmoke::item).allMatch(output ->
            owner.getStats().getValue(Stats.ITEM_CRAFTED.get(output)) == 1 && inventory.countItem(output) == 1);
        boolean consumed = List.of(Items.MILK_BUCKET, Items.WHEAT, Items.BOWL, EIOItems.POWDERED_ENDER_PEARL.get(),
            EIOItems.SOULARIUM_INGOT.get(), EIOItems.VIBRANT_ALLOY_INGOT.get(), item("fused_quartz")).stream()
            .noneMatch(input -> inventory.countItem(input) != 0);
        return new Crafted(cereal.getHoverName().getString(), cereal.hasCustomHoverName(), inventory.countItem(Items.BUCKET),
            craftedOnce, consumed);
    }

    private void prepareWorld(IntegratedServer server) {
        var level = server.overworld();
        var rules = level.getGameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_DOFIRETICK).set(false, server);
        rules.getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server);
        rules.getRule(GameRules.RULE_LIMITED_CRAFTING).set(true, server);
        level.setDayTime(1000);
        for (int x = -7; x <= 15; x++) {
            for (int z = -8; z <= 8; z++) level.setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(TABLE, Blocks.CRAFTING_TABLE.defaultBlockState());
        if (!special) {
            for (int x = 6; x < 14; x++) {
                for (int z = -4; z < 4; z++) {
                    var fire = new BlockPos(x, 80, z);
                    level.setBlockAndUpdate(fire.below(), Blocks.BEDROCK.defaultBlockState());
                    level.setBlockAndUpdate(fire, Blocks.FIRE.defaultBlockState());
                }
            }
        }
        var player = owner(server);
        var inventory = player.getInventory();
        inventory.setItem(9, new ItemStack(Items.BOWL));
        inventory.setItem(10, new ItemStack(Items.MILK_BUCKET));
        inventory.setItem(11, new ItemStack(Items.WHEAT));
        inventory.setItem(12, new ItemStack(EIOItems.POWDERED_ENDER_PEARL.get()));
        if (!special) {
            inventory.setItem(13, new ItemStack(EIOItems.SOULARIUM_INGOT.get(), 3));
            inventory.setItem(14, new ItemStack(EIOItems.VIBRANT_ALLOY_INGOT.get()));
            inventory.setItem(15, new ItemStack(item("fused_quartz"), 3));
        }
        player.connection.teleport(TABLE_STAND.x, TABLE_STAND.y, TABLE_STAND.z, 180, 15);
    }

    private static FireDrops collectFireDrops(IntegratedServer server) {
        var drops = server.overworld().getEntitiesOfClass(ItemEntity.class, FIRE_AREA, entity ->
            entity.getItem().is(EIOItems.SUSPICIOUS_SEED.get()) || entity.getItem().is(EIOItems.GRAINS_OF_INFINITY.get()));
        int seeds = 0;
        int grains = 0;
        for (var drop : drops) {
            var stack = drop.getItem();
            if (stack.is(EIOItems.SUSPICIOUS_SEED.get())) seeds += stack.getCount();
            else grains += stack.getCount();
        }
        if (seeds >= 3 && grains >= 2) {
            drops.forEach(drop -> drop.teleportTo(TABLE_STAND.x, TABLE_STAND.y, TABLE_STAND.z));
        }
        return new FireDrops(seeds, grains);
    }

    private static ServerPlayer owner(IntegratedServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    private void click(int menu, int slot, ClickType type) {
        minecraft.gameMode.handleInventoryMouseClick(menu, slot, 0, type, minecraft.player);
    }

    private static List<Slot> grid(CraftingMenu menu) {
        return menu.slots.subList(1, 10);
    }

    private Recipe<?> recipe(String id) {
        return minecraft.level.getRecipeManager().byKey(ResourceLocation.parse("enderio:" + id)).orElseThrow();
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("enderio:" + id)).orElseThrow();
    }

    private int count(String id) {
        return minecraft.player.getInventory().countItem(item(id));
    }

    private void capture(String name) throws Exception {
        var data = ClientSmoke.capture(name + ".png");
        captures.put(name, data);
        check(name + "_silent_background_render", Boolean.TRUE.equals(data.get("passed")));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        minecraft.options.keyUse.setDown(false);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_xp_crafting", result);
    }

    private record FireDrops(int seeds, int grains) {}
    private record Crafted(String name, boolean customName, int bucketCount, boolean craftedOnce, boolean ingredientsConsumed) {}
    private record Meal(int food, float saturation, boolean bowl, Vec3 position, double chance) {}
}
