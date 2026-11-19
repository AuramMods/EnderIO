package com.enderio.port.clientqa;

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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Crafts the Alloy Smelter outputs through their stock block and nugget recipes in survival. */
final class ClientAlloyProgressionSmoke {
    private enum Stage { TITLE, JOIN, CHEST, TAKE, TAKEN, TABLE, RECIPE, RESULT, COLLECT, PLACE, PLACED, VERIFY, DONE }

    private record Alloy(String id, int produced, int remainder) {
        String ingot() { return id + "_ingot"; }
        String nugget() { return id + "_nugget"; }
        String block() { return id + "_block"; }
    }

    private record Craft(String name, String recipe, String input, int inputs, String result, int results) {}

    private static final BlockPos CHEST = new BlockPos(3211, 80, 10);
    private static final BlockPos TABLE = new BlockPos(3210, 80, 10);
    private static final Vec3 STAND = new Vec3(3210.5, 80, 12.5);
    private static final List<Alloy> ALLOYS = List.of(
        new Alloy("redstone_alloy", 9, 0),
        new Alloy("pulsating_alloy", 10, 1),
        new Alloy("dark_steel", 10, 1),
        new Alloy("soularium", 10, 1),
        new Alloy("end_steel", 20, 11),
        new Alloy("conductive_alloy", 10, 1),
        new Alloy("energetic_alloy", 10, 1),
        new Alloy("vibrant_alloy", 10, 1));
    private static final List<Craft> RECIPES = recipes();

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int recipeIndex;
    private int placementIndex;
    private int inputBefore;
    private int resultBefore;
    private int unlockBy;

    ClientAlloyProgressionSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private static List<Craft> recipes() {
        var crafts = new ArrayList<Craft>();
        for (var alloy : ALLOYS) {
            crafts.add(new Craft(alloy.id() + "_pack_block", alloy.block(), alloy.ingot(), 9, alloy.block(), 1));
            crafts.add(new Craft(alloy.id() + "_unpack_block", alloy.ingot(), alloy.block(), 1, alloy.ingot(), 9));
            crafts.add(new Craft(alloy.id() + "_make_nuggets", alloy.nugget(), alloy.ingot(), 1, alloy.nugget(), 9));
            crafts.add(new Craft(alloy.id() + "_pack_nuggets", "item.enderio." + alloy.nugget() + "_to_ingot",
                alloy.nugget(), 9, alloy.ingot(), 1));
            crafts.add(new Craft(alloy.id() + "_make_final_block", alloy.block(), alloy.ingot(), 9, alloy.block(), 1));
        }
        return List.copyOf(crafts);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 168_000_000_000L) {
                String recipe = recipeIndex < RECIPES.size() ? RECIPES.get(recipeIndex).name() : "all crafts complete";
                throw new AssertionError("Alloy progression stopped at " + stage + ", " + recipe);
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
                    if (!Files.exists(output.resolve("server-ready")) || player.distanceToSqr(STAND) > 0.1) return;
                    open(CHEST);
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)) return;
                    var menu = screen.getMenu();
                    int outputs = ALLOYS.size() + 1; // Soul sand and soul soil have separate output stacks.
                    if (menu.slots.subList(0, 27).stream().filter(Slot::hasItem).count() != outputs) return;
                    capture("machine-outputs");
                    for (int slot = 0; slot < outputs; slot++) quickMove(menu, slot);
                    stage = Stage.TAKEN;
                }
                case TAKEN -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)
                        || screen.getMenu().slots.subList(0, 27).stream().anyMatch(Slot::hasItem)) return;
                    check("survival_collects_all_original_alloy_counts",
                        ALLOYS.stream().allMatch(alloy -> count(item(alloy.ingot())) == alloy.produced()));
                    player.closeContainer();
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (minecraft.screen != null) return;
                    open(TABLE);
                    unlockBy = player.tickCount + 120;
                    stage = Stage.RECIPE;
                }
                case RECIPE, RESULT, COLLECT -> craft(RECIPES.get(recipeIndex));
                case PLACE, PLACED -> place(ALLOYS.get(placementIndex));
                case VERIFY -> {
                    if (!Files.exists(output.resolve("server-done"))) return;
                    capture("placed-stock-alloys");
                    finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft(Craft step) throws IOException {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var player = minecraft.player;
        var menu = screen.getMenu();
        var grid = menu.slots.subList(1, 10);
        Item input = item(step.input());
        Item result = item(step.result());
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(id(step.recipe())).orElseThrow();
                if (!player.getRecipeBook().contains(recipe)) {
                    if (player.tickCount >= unlockBy) {
                        throw new AssertionError("Recipe " + recipe.getId() + " stayed locked with " + count(input) + " of " + step.input());
                    }
                    return;
                }
                inputBefore = count(input);
                resultBefore = count(result);
                check(step.name() + "_naturally_unlocked", inputBefore >= step.inputs() && grid.stream().noneMatch(Slot::hasItem));
                if (step.result().endsWith("_nugget")) {
                    var reverse = minecraft.level.getRecipeManager().byKey(id("item.enderio." + step.result() + "_to_ingot")).orElseThrow();
                    check(step.name() + "_reverse_unlock_waits_for_nuggets",
                        resultBefore == 0 && !player.getRecipeBook().contains(reverse));
                }
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                var stack = menu.getSlot(0).getItem();
                var used = grid.stream().filter(Slot::hasItem).toList();
                if (!stack.is(result) || used.size() != step.inputs()) return;
                check(step.name() + "_stock_grid_and_result",
                    stack.getCount() == step.results() && used.stream()
                        .allMatch(slot -> slot.getItem().is(input) && slot.getItem().getCount() == 1));
                if (step.name().equals("end_steel_make_final_block")) capture("end-steel-recipe-grid");
                quickMove(menu, 0);
                stage = Stage.COLLECT;
            }
            case COLLECT -> {
                if (grid.stream().anyMatch(Slot::hasItem) || count(result) != resultBefore + step.results()) return;
                check(step.name() + "_preserves_exact_counts",
                    !menu.getSlot(0).hasItem() && menu.getCarried().isEmpty() && count(input) == inputBefore - step.inputs());
                if (++recipeIndex == RECIPES.size()) {
                    check("all_nuggets_round_trip_without_losing_material", ALLOYS.stream().allMatch(alloy ->
                        count(item(alloy.nugget())) == 0 && count(item(alloy.ingot())) == alloy.remainder()
                            && count(item(alloy.block())) == 1));
                    player.closeContainer();
                    stage = Stage.PLACE;
                } else {
                    unlockBy = player.tickCount + 120;
                    stage = Stage.RECIPE;
                }
            }
            default -> {}
        }
    }

    private void place(Alloy alloy) throws IOException {
        if (minecraft.screen != null) return;
        var pos = new BlockPos(3208 + placementIndex % 5, 80, 9 + 2 * (placementIndex / 5));
        var block = BuiltInRegistries.BLOCK.getOptional(id(alloy.block())).orElseThrow();
        if (stage == Stage.PLACE) {
            if (!equip(block.asItem())) return;
            var hit = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
            check(alloy.id() + "_native_survival_placement",
                minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
            stage = Stage.PLACED;
        } else if (minecraft.level.getBlockState(pos).is(block) && count(block.asItem()) == 0) {
            if (++placementIndex == ALLOYS.size()) {
                signal("placed");
                stage = Stage.VERIFY;
            } else {
                stage = Stage.PLACE;
            }
        }
    }

    private boolean equip(Item item) {
        var player = minecraft.player;
        var inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (!inventory.getItem(slot).is(item)) continue;
            inventory.selected = slot;
            return true;
        }
        for (int slot = 9; slot < 36; slot++) {
            if (!inventory.getItem(slot).is(item)) continue;
            minecraft.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, slot, 0, ClickType.SWAP, player);
            return false;
        }
        throw new AssertionError("Missing crafted item " + BuiltInRegistries.ITEM.getKey(item));
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Alloy Progression", address, true), false);
        stage = Stage.JOIN;
    }

    private void open(BlockPos pos) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
        check("native_menu_at_" + pos.getX(),
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
    }

    private void quickMove(AbstractContainerMenu menu, int slot) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.QUICK_MOVE, minecraft.player);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("enderio", path);
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.getOptional(id(path)).orElseThrow();
    }

    private int count(Item item) {
        return minecraft.player.getInventory().countItem(item);
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_hidden_muted", Boolean.TRUE.equals(frame.get("passed")));
    }

    private void signal(String name) throws IOException {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
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
        ClientSmoke.complete("client_alloy_progression", result);
    }
}
