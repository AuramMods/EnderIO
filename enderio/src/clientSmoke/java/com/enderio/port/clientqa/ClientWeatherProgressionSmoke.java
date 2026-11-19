package com.enderio.port.clientqa;

import com.enderio.enderio.client.content.machines.gui.screen.WeatherObeliskScreen;
import com.enderio.enderio.content.machines.obelisks.weather.WeatherObeliskMenu;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOFluids;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.Craft;
import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.id;
import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.item;

final class ClientWeatherProgressionSmoke {
    private enum Stage { TITLE, JOIN, CHEST, TAKE, TABLE, RECIPE, RESULT, COLLECT, PLACE, PLACED, OPEN, INSERT, LOADED, RAIN, DONE }

    private static final BlockPos CHEST = new BlockPos(3453, 80, 8);
    private static final BlockPos TABLE = new BlockPos(3452, 80, 8);
    private static final BlockPos OBELISK = new BlockPos(3457, 80, 8);
    private static final Vec3 WORKSHOP = new Vec3(3452.5, 80, 11.5);
    private static final Vec3 OUTSIDE = new Vec3(3457.5, 80, 11.5);
    private static final List<Craft> RECIPES = List.of(
        Craft.shaped("weather_crystal", 1, " P VEV P ", Map.of(
            'P', "pulsating_crystal", 'V', "vibrant_crystal", 'E', "ender_crystal")),
        Craft.shaped("weather_obelisk", 1, " W ESESCS", Map.of(
            'W', "weather_crystal", 'E', "energetic_alloy_ingot", 'S', "soularium_ingot", 'C', "void_chassis")));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int actionAt = -1;
    private int recipeIndex;

    ClientWeatherProgressionSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 172_000_000_000L) {
                throw new AssertionError("Weather progression stopped at " + stage + ", recipe " + recipeIndex);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = Stage.CHEST;
                }
                case CHEST -> {
                    if (!settled("workshop", WORKSHOP)) return;
                    check("native_workshop_chest", use(CHEST, Direction.SOUTH));
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)) return;
                    var menu = screen.getMenu();
                    if (menu.slots.subList(0, 27).stream().filter(Slot::hasItem).count() != 8) return;
                    for (int slot = 0; slot < 8; slot++) quickMove(menu, slot);
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)
                        || screen.getMenu().slots.subList(0, 27).stream().anyMatch(Slot::hasItem)) return;
                    check("the_original_bound_crystal_and_returned_vial_enter_the_survival_inventory",
                        count("ender_crystal") == 1 && count("soul_vial") == 1);
                    var obelisk = minecraft.level.getRecipeManager().byKey(id("weather_obelisk")).orElseThrow();
                    check("the_obelisk_recipe_waits_for_the_weather_crystal", !player.getRecipeBook().contains(obelisk));
                    player.closeContainer();
                    check("native_crafting_table", use(TABLE, Direction.SOUTH));
                    stage = Stage.RECIPE;
                }
                case RECIPE, RESULT, COLLECT -> craft(RECIPES.get(recipeIndex));
                case PLACE -> {
                    if (!settled("place", OUTSIDE)) return;
                    var inventory = player.getInventory();
                    int slot = inventory.findSlotMatchingItem(new ItemStack(EIOBlocks.WEATHER_OBELISK.asItem()));
                    if (slot < 0) throw new AssertionError("Missing crafted Weather Obelisk");
                    if (slot >= 9) {
                        minecraft.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, slot, 0, ClickType.SWAP, player);
                        return;
                    }
                    inventory.selected = slot;
                    check("native_survival_placement_of_the_original_crafted_obelisk", use(OBELISK.below(), Direction.UP));
                    stage = Stage.PLACED;
                }
                case PLACED -> {
                    if (!minecraft.level.getBlockState(OBELISK).is(EIOBlocks.WEATHER_OBELISK.get()) || count("weather_obelisk") != 0) return;
                    signal("placed");
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    if (!settled("open", OUTSIDE)) return;
                    check("the_player_opens_the_weather_obelisk_it_just_placed", use(OBELISK, Direction.SOUTH));
                    stage = Stage.INSERT;
                }
                case INSERT, LOADED, RAIN -> operate();
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft(Craft craft) throws IOException {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        var grid = menu.slots.subList(1, 10);
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(id(craft.recipe())).orElseThrow();
                if (!minecraft.player.getRecipeBook().contains(recipe)) return;
                check(craft.name() + "_unlocks_with_the_exact_survival_ingredients", count(craft.result()) == 0
                    && grid.stream().noneMatch(Slot::hasItem) && craft.inputs().entrySet().stream().allMatch(entry ->
                    count(entry.getKey()) == entry.getValue()));
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                var stack = menu.getSlot(0).getItem();
                if (!stack.is(item(craft.result())) || grid.stream().filter(Slot::hasItem).count() != craft.ingredientCount()) return;
                check(craft.name() + "_uses_the_stock_arrangement_and_makes_one_item",
                    craft.matchesGrid(grid) && stack.getCount() == craft.results());
                capture(craft.result() + "-recipe");
                quickMove(menu, 0);
                stage = Stage.COLLECT;
            }
            case COLLECT -> {
                if (grid.stream().anyMatch(Slot::hasItem) || count(craft.result()) != craft.results()) return;
                check(craft.name() + "_spends_all_its_original_ingredients_once", !menu.getSlot(0).hasItem()
                    && menu.getCarried().isEmpty() && craft.inputs().keySet().stream().allMatch(input -> count(input) == 0));
                if (++recipeIndex == RECIPES.size()) {
                    minecraft.player.closeContainer();
                    signal("crafted");
                    stage = Stage.PLACE;
                } else stage = Stage.RECIPE;
            }
            default -> {}
        }
    }

    private void operate() throws IOException {
        if (!(minecraft.screen instanceof WeatherObeliskScreen screen)) return;
        var menu = screen.getMenu();
        switch (stage) {
            case INSERT -> {
                var player = minecraft.player;
                var slot = menu.slots.stream().filter(candidate -> candidate.container == player.getInventory()
                    && candidate.getItem().is(Items.FIREWORK_ROCKET)).findFirst();
                if (slot.isEmpty()) return;
                quickMove(menu, slot.get().index);
                stage = Stage.LOADED;
            }
            case LOADED -> {
                if (!loaded(menu, 1237, 2)) return;
                check("the_menu_syncs_cloud_seed_and_two_original_rockets_under_the_roof",
                    menu.getCraftingProgress() == 0 && !minecraft.level.isRaining()
                        && minecraft.player.getInventory().countItem(Items.FIREWORK_ROCKET) == 0);
                capture("under-roof");
                signal("loaded");
                stage = Stage.RAIN;
            }
            case RAIN -> {
                if (!ready("rain") || !minecraft.level.isRaining() || !loaded(menu, 237, 1)) return;
                check("the_crafted_obelisk_menu_shows_rain_and_exact_remainders",
                    menu.getCraftingProgress() == 0 && !minecraft.level.isThundering() && count("soul_vial") == 1);
                capture("weather-rain");
                finish(null);
            }
            default -> {}
        }
    }

    private boolean loaded(WeatherObeliskMenu menu, int amount, int rockets) {
        var tank = menu.getFluidTank();
        var stack = menu.getSlot(WeatherObeliskMenu.INPUTS_INDEX).getItem();
        return tank != null && tank.capacity() == 3000 && tank.contents().getFluid() == EIOFluids.CLOUD_SEED.source().get()
            && tank.contents().getAmount() == amount && stack.is(Items.FIREWORK_ROCKET) && stack.getCount() == rockets;
    }

    private int count(String name) {
        return minecraft.player.getInventory().countItem(item(name));
    }

    private boolean use(BlockPos pos, Direction face) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
        return minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction();
    }

    private boolean settled(String name, Vec3 position) {
        var player = minecraft.player;
        if (!ready(name) || minecraft.screen != null || player.distanceToSqr(position) > 0.15) {
            actionAt = -1;
            return false;
        }
        if (actionAt < 0) actionAt = player.tickCount + 8;
        if (player.tickCount < actionAt) return false;
        actionAt = -1;
        return true;
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Weather Progression", address, true), false);
        stage = Stage.JOIN;
    }

    private void quickMove(AbstractContainerMenu menu, int slot) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.QUICK_MOVE, minecraft.player);
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve("server-" + name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var temporary = directory.resolve(name + ".tmp");
        Files.writeString(temporary, "{}");
        Files.move(temporary, directory.resolve(name + ".json"));
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_hidden_muted", Boolean.TRUE.equals(frame.get("passed")));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_weather_progression", result);
    }
}
