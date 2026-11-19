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
import net.minecraft.world.level.block.RotatedPillarBlock;
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

/** Crafts the Alloy Smelter outputs through stock recipes in survival. */
final class ClientAlloyProgressionSmoke {
    private enum Stage { TITLE, JOIN, CHEST, TAKE, TAKEN, TABLE, RECIPE, RESULT, COLLECT, PLACE, PLACED, VERIFY, DONE }

    private record Alloy(String id, int produced, int remainder) {
        String ingot() { return id + "_ingot"; }
        String nugget() { return id + "_nugget"; }
        String block() { return id + "_block"; }
    }

    private record Craft(String name, String recipe, Map<String, Integer> inputs, String result, int results, List<String> layout) {
        Craft(String name, String recipe, String input, int inputs, String result, int results) {
            this(name, recipe, Map.of(input, inputs), result, results, List.of());
        }

        static Craft shaped(String recipe, int results, String pattern, Map<Character, String> key) {
            var inputs = new LinkedHashMap<String, Integer>();
            var layout = new ArrayList<String>(9);
            for (char symbol : pattern.toCharArray()) {
                String input = symbol == ' ' ? "" : key.get(symbol);
                layout.add(input);
                if (!input.isEmpty()) inputs.merge(input, 1, Integer::sum);
            }
            return new Craft(recipe, recipe, inputs, recipe, results, layout);
        }

        int ingredientCount() {
            return inputs.values().stream().mapToInt(Integer::intValue).sum();
        }

        boolean matchesGrid(List<Slot> grid) {
            if (layout.isEmpty()) {
                Item input = item(inputs.keySet().iterator().next());
                return grid.stream().filter(Slot::hasItem)
                    .allMatch(slot -> slot.getItem().is(input) && slot.getItem().getCount() == 1);
            }
            for (int index = 0; index < layout.size(); index++) {
                String input = layout.get(index);
                var stack = grid.get(index).getItem();
                if (input.isEmpty() ? !stack.isEmpty() : !stack.is(item(input)) || stack.getCount() != 1) return false;
            }
            return true;
        }
    }

    private record Placement(String label, String block, BlockPos position, Direction face, int itemsAfter) {}

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
    private static final List<Craft> ALLOY_RECIPES = alloyRecipes();
    private static final List<Craft> COMPONENT_RECIPES = componentRecipes();
    private static final List<Placement> COMPONENT_PLACEMENTS = List.of(
        new Placement("ensouled_chassis", "ensouled_chassis", new BlockPos(3210, 80, 11), Direction.UP, 0),
        new Placement("vertical_soul_chain", "soul_chain", new BlockPos(3212, 80, 11), Direction.UP, 1),
        new Placement("horizontal_soul_chain", "soul_chain", new BlockPos(3209, 80, 11), Direction.WEST, 0));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private final boolean components;
    private final List<Craft> recipes;
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int recipeIndex;
    private int placementIndex;
    private Map<String, Integer> inputsBefore = Map.of();
    private int resultBefore;
    private int unlockBy;
    private int openChestAt = -1;

    ClientAlloyProgressionSmoke(boolean components) {
        this.components = components;
        recipes = components ? COMPONENT_RECIPES : ALLOY_RECIPES;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private static List<Craft> alloyRecipes() {
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

    private static List<Craft> componentRecipes() {
        var ironGear = gear("iron_gear", "minecraft:iron", "grains_of_infinity");
        var soulChain = Craft.shaped("soul_chain", 2, " N QIQ N ", Map.of(
            'N', "soularium_nugget", 'Q', "powdered_quartz", 'I', "soularium_ingot"));
        return List.of(nuggets("minecraft:iron"), ironGear, ironGear,
            nuggets("energetic_alloy"), gear("energized_gear", "energetic_alloy", "iron_gear"),
            nuggets("vibrant_alloy"), gear("vibrant_gear", "vibrant_alloy", "energized_gear"),
            nuggets("dark_steel"), gear("dark_bimetal_gear", "dark_steel", "iron_gear"),
            nuggets("soularium"), soulChain, soulChain, soulChain,
            Craft.shaped("ensouled_chassis", 1, "CICIQICIC", Map.of(
                'C', "soul_chain", 'I', "soularium_ingot", 'Q', "minecraft:quartz")));
    }

    private static Craft gear(String recipe, String metal, String center) {
        return Craft.shaped(recipe, 1, "NINIGININ", Map.of(
            'N', metal + "_nugget", 'I', metal + "_ingot", 'G', center));
    }

    private static Craft nuggets(String metal) {
        String nugget = metal + "_nugget";
        return new Craft(nugget, nugget, metal + "_ingot", 1, nugget, 9);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 168_000_000_000L) {
                String recipe = recipeIndex < recipes.size() ? recipes.get(recipeIndex).name() : "all crafts complete";
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
                    if (openChestAt < 0) openChestAt = player.tickCount + 8;
                    if (player.tickCount < openChestAt) return;
                    open(CHEST);
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)) return;
                    var menu = screen.getMenu();
                    int outputs = ALLOYS.size() + 1 + (components ? 4 : 0);
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
                    if (components) {
                        check("component_inputs_include_the_original_ground_quartz", count(item("powdered_quartz")) >= 6
                            && count(item("minecraft:quartz")) == 1 && count(item("minecraft:iron_ingot")) == 9
                            && count(item("grains_of_infinity")) == 2);
                        check("gear_progression_waits_for_base_gears", !knows("energized_gear") && !knows("vibrant_gear") && !knows("dark_bimetal_gear"));
                    }
                    player.closeContainer();
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (minecraft.screen != null) return;
                    open(TABLE);
                    unlockBy = player.tickCount + 120;
                    stage = Stage.RECIPE;
                }
                case RECIPE, RESULT, COLLECT -> craft(recipes.get(recipeIndex));
                case PLACE, PLACED -> {
                    if (components) placeComponent(COMPONENT_PLACEMENTS.get(placementIndex));
                    else place(ALLOYS.get(placementIndex));
                }
                case VERIFY -> {
                    if (!Files.exists(output.resolve("server-done"))) return;
                    capture(components ? "placed-chassis-and-chains" : "placed-stock-alloys");
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
        Item result = item(step.result());
        String label = components ? recipeIndex + "_" + step.name() : step.name();
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(id(step.recipe())).orElseThrow();
                if (!player.getRecipeBook().contains(recipe)) {
                    if (player.tickCount >= unlockBy) {
                        throw new AssertionError("Recipe " + recipe.getId() + " stayed locked with " + inventoryCounts(step.inputs()));
                    }
                    return;
                }
                inputsBefore = inventoryCounts(step.inputs());
                resultBefore = count(result);
                check(label + "_naturally_unlocked", step.inputs().entrySet().stream().allMatch(entry ->
                    inputsBefore.get(entry.getKey()) >= entry.getValue()) && grid.stream().noneMatch(Slot::hasItem));
                if (!components && step.result().endsWith("_nugget")) {
                    var reverse = minecraft.level.getRecipeManager().byKey(id("item.enderio." + step.result() + "_to_ingot")).orElseThrow();
                    check(step.name() + "_reverse_unlock_waits_for_nuggets",
                        resultBefore == 0 && !player.getRecipeBook().contains(reverse));
                }
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                var stack = menu.getSlot(0).getItem();
                if (!stack.is(result) || grid.stream().filter(Slot::hasItem).count() != step.ingredientCount()) return;
                check(label + "_stock_grid_and_result", stack.getCount() == step.results() && step.matchesGrid(grid),
                    "native grid " + grid.stream().map(Slot::getItem).toList() + " produced " + stack);
                if (step.name().equals("end_steel_make_final_block")) capture("end-steel-recipe-grid");
                if (step.name().equals("ensouled_chassis")) capture("ensouled-chassis-grid");
                if (step.name().equals("vibrant_gear")) capture("vibrant-gear-grid");
                quickMove(menu, 0);
                stage = Stage.COLLECT;
            }
            case COLLECT -> {
                if (grid.stream().anyMatch(Slot::hasItem) || count(result) != resultBefore + step.results()) return;
                check(label + "_preserves_exact_counts", !menu.getSlot(0).hasItem() && menu.getCarried().isEmpty()
                    && step.inputs().entrySet().stream().allMatch(entry ->
                        count(item(entry.getKey())) == inputsBefore.get(entry.getKey()) - entry.getValue()));
                if (++recipeIndex == recipes.size()) {
                    if (!components) {
                        check("all_nuggets_round_trip_without_losing_material", ALLOYS.stream().allMatch(alloy ->
                            count(item(alloy.nugget())) == 0 && count(item(alloy.ingot())) == alloy.remainder()
                                && count(item(alloy.block())) == 1));
                    }
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

    private Map<String, Integer> inventoryCounts(Map<String, Integer> ingredients) {
        var counts = new LinkedHashMap<String, Integer>();
        for (String ingredient : ingredients.keySet()) counts.put(ingredient, count(item(ingredient)));
        return counts;
    }

    private boolean knows(String name) {
        return minecraft.player.getRecipeBook().contains(minecraft.level.getRecipeManager().byKey(id(name)).orElseThrow());
    }

    private void placeComponent(Placement step) throws IOException {
        if (minecraft.screen != null) return;
        var block = BuiltInRegistries.BLOCK.getOptional(id(step.block())).orElseThrow();
        if (stage == Stage.PLACE) {
            if (!equip(block.asItem())) return;
            BlockPos support = step.position().relative(step.face().getOpposite());
            var hit = new BlockHitResult(Vec3.atCenterOf(support).relative(step.face(), 0.5), step.face(), support, false);
            check(step.label() + "_native_placement",
                minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
            stage = Stage.PLACED;
            return;
        }
        var state = minecraft.level.getBlockState(step.position());
        if (!state.is(block) || count(block.asItem()) != step.itemsAfter()) return;
        check(step.label() + "_placement_state", !state.hasProperty(RotatedPillarBlock.AXIS)
            || state.getValue(RotatedPillarBlock.AXIS) == step.face().getAxis());
        if (++placementIndex == COMPONENT_PLACEMENTS.size()) {
            signal("placed");
            stage = Stage.VERIFY;
        } else {
            stage = Stage.PLACE;
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
        return path.contains(":") ? ResourceLocation.parse(path) : ResourceLocation.fromNamespaceAndPath("enderio", path);
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
        check(name, passed, "");
    }

    private void check(String name, boolean passed, String details) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(details.isEmpty() ? name : name + ": " + details);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete(components ? "client_advanced_components" : "client_alloy_progression", result);
    }
}
