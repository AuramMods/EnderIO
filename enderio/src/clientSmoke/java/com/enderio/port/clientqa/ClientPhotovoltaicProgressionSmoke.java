package com.enderio.port.clientqa;

import com.enderio.enderio.content.machines.solar_panel.SolarPanelBlockEntity;
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

final class ClientPhotovoltaicProgressionSmoke {
    private enum Stage { TITLE, JOIN, CHEST, TAKE, TABLE, RECIPE, RESULT, STORE, COLLECT, PLATES, PLACE, PLACED, VERIFY, DONE }

    private static final BlockPos CHEST = new BlockPos(3569, 80, 8);
    private static final BlockPos TABLE = new BlockPos(3568, 80, 8);
    private static final BlockPos PANEL = new BlockPos(3574, 80, 8);
    private static final Vec3 WORKSHOP = new Vec3(3568.5, 80, 11.5);
    private static final Vec3 PANEL_STAND = new Vec3(3574.5, 80, 11.5);
    private static final String COMPOSITE = "photovoltaic_composite";
    private static final String PLATE = "photovoltaic_plate";
    private static final String MODULE = "energetic_photovoltaic_module";
    private static final int BATCHES = 6;
    private static final Craft COMPOSITE_RECIPE = new Craft(COMPOSITE, COMPOSITE,
        Map.of("powdered_lapis_lazuli", 1, "powdered_coal", 1, "silicon", 1), COMPOSITE, 1, List.of());
    private static final Craft MODULE_RECIPE = Craft.shaped(MODULE, 1, "GFGPPPCDC", Map.of(
        'C', "basic_capacitor", 'D', "minecraft:redstone", 'G', "minecraft:gold_ingot",
        'F', "minecraft:glass", 'P', PLATE));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int actionAt = -1;
    private int craftedComposites;
    private int resultBefore;
    private boolean buildingModule;
    private Map<String, Integer> inputsBefore = Map.of();

    ClientPhotovoltaicProgressionSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 154_000_000_000L) {
                String screen = minecraft.screen == null ? "world" : minecraft.screen.getClass().getSimpleName();
                throw new AssertionError("Photovoltaic progression stopped at " + stage + ", composites " + craftedComposites + ", screen " + screen);
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
                    check("the_composite_and_module_unlocks_wait_for_original_ingredients", !knows(COMPOSITE_RECIPE) && !knows(MODULE_RECIPE));
                    signal("connected");
                    stage = Stage.CHEST;
                }
                case CHEST -> {
                    if (!settled("workshop", WORKSHOP)) return;
                    check("native_workshop_chest_for_ground_dusts", use(CHEST, Direction.SOUTH));
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)) return;
                    var menu = screen.getMenu();
                    int stacks = buildingModule ? 5 : 3;
                    if (menu.slots.subList(0, 27).stream().filter(Slot::hasItem).count() != stacks) return;
                    for (int slot = 0; slot < stacks; slot++) click(menu, slot, ClickType.QUICK_MOVE);
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)
                        || screen.getMenu().slots.subList(0, 27).stream().anyMatch(Slot::hasItem)) return;
                    player.closeContainer();
                    check(buildingModule ? "native_crafting_table_for_module" : "native_crafting_table_for_composite", use(TABLE, Direction.SOUTH));
                    stage = Stage.RECIPE;
                }
                case RECIPE, RESULT, STORE, COLLECT -> craft(buildingModule ? MODULE_RECIPE : COMPOSITE_RECIPE);
                case PLATES -> {
                    if (!settled("plates", WORKSHOP)) return;
                    check("native_workshop_chest_for_the_original_smelter_plates", use(CHEST, Direction.SOUTH));
                    buildingModule = true;
                    stage = Stage.TAKE;
                }
                case PLACE -> {
                    if (!settled("place", PANEL_STAND) || !equipModule()) return;
                    check("native_survival_placement_consumes_the_original_module", use(PANEL.below(), Direction.UP));
                    stage = Stage.PLACED;
                }
                case PLACED -> {
                    if (!(minecraft.level.getBlockEntity(PANEL) instanceof SolarPanelBlockEntity) || count(MODULE) != 0) return;
                    capture("original-module-placed");
                    signal("placed");
                    stage = Stage.VERIFY;
                }
                case VERIFY -> {
                    if (ready("done")) finish(null);
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
        var menu = screen.getMenu();
        var grid = menu.slots.subList(1, 10);
        String label = buildingModule ? MODULE : COMPOSITE + "_" + (craftedComposites + 1);
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(id(step.recipe())).orElseThrow();
                if (!minecraft.player.getRecipeBook().contains(recipe)) return;
                inputsBefore = new LinkedHashMap<>();
                for (String ingredient : step.inputs().keySet()) inputsBefore.put(ingredient, count(ingredient));
                resultBefore = count(step.result());
                check(label + "_unlocks_and_accepts_original_items", grid.stream().noneMatch(Slot::hasItem)
                    && step.inputs().entrySet().stream().allMatch(entry -> inputsBefore.get(entry.getKey()) >= entry.getValue()));
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                var stack = menu.getSlot(0).getItem();
                if (!stack.is(item(step.result())) || grid.stream().filter(Slot::hasItem).count() != step.ingredientCount()) return;
                check(label + "_uses_the_stock_grid_and_yield", step.matchesGrid(grid) && stack.getCount() == 1);
                if (buildingModule || craftedComposites == 0) capture(step.result() + "-recipe");
                click(menu, 0, ClickType.PICKUP);
                stage = Stage.STORE;
            }
            case STORE -> {
                var carried = menu.getCarried();
                if (!carried.is(item(step.result())) || carried.getCount() != 1) return;
                var inventory = minecraft.player.getInventory();
                var slots = menu.slots.stream().filter(slot -> slot.container == inventory).toList();
                var target = slots.stream().filter(slot -> slot.getItem().is(carried.getItem())
                    && slot.getItem().getCount() < carried.getMaxStackSize()).findFirst()
                    .orElseGet(() -> slots.stream().filter(slot -> !slot.hasItem()).findFirst().orElseThrow());
                click(menu, target.index, ClickType.PICKUP);
                stage = Stage.COLLECT;
            }
            case COLLECT -> {
                if (grid.stream().anyMatch(Slot::hasItem) || count(step.result()) != resultBefore + 1
                    || !menu.getCarried().isEmpty() || menu.getSlot(0).hasItem()) return;
                if (!step.inputs().entrySet().stream().allMatch(entry ->
                    count(entry.getKey()) == inputsBefore.get(entry.getKey()) - entry.getValue())) return;
                check(label + "_spends_the_original_ingredients_once", true);
                if (buildingModule) {
                    minecraft.player.closeContainer();
                    signal("crafted");
                    stage = Stage.PLACE;
                } else if (++craftedComposites == BATCHES) {
                    minecraft.player.closeContainer();
                    signal("composites");
                    stage = Stage.PLATES;
                } else stage = Stage.RECIPE;
            }
            default -> {}
        }
    }

    private boolean equipModule() {
        var player = minecraft.player;
        int slot = player.getInventory().findSlotMatchingItem(new ItemStack(item(MODULE)));
        if (slot < 0) throw new AssertionError("Missing crafted photovoltaic module");
        if (slot >= 9) {
            click(player.inventoryMenu, slot, ClickType.SWAP);
            return false;
        }
        player.getInventory().selected = slot;
        return true;
    }

    private boolean knows(Craft craft) {
        return minecraft.player.getRecipeBook().contains(minecraft.level.getRecipeManager().byKey(id(craft.recipe())).orElseThrow());
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
            new ServerData("Ender IO Photovoltaic Progression", address, true), false);
        stage = Stage.JOIN;
    }

    private void click(AbstractContainerMenu menu, int slot, ClickType type) {
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, type, minecraft.player);
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
        ClientSmoke.complete("client_photovoltaic_progression", result);
    }
}
