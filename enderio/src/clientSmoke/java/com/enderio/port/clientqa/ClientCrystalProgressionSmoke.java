package com.enderio.port.clientqa;

import com.enderio.enderio.api.components.GrindingBallData;
import com.enderio.enderio.client.content.machines.gui.screen.SagMillScreen;
import com.enderio.enderio.content.machines.sag_mill.SagMillMenu;
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

final class ClientCrystalProgressionSmoke {
    private enum Stage { TITLE, JOIN, CHEST, TAKE, TABLE, RECIPE, RESULT, COLLECT, MILL, INSERT, INSERTED, PAID, VERIFY, DONE }

    private record Route(String kind, String gem, int x) {
        String crystal() { return kind + "_crystal"; }
        String nugget() { return kind + "_alloy_nugget"; }
        BlockPos mill() { return new BlockPos(x, 80, 8); }
        Vec3 stand() { return new Vec3(x + 0.5, 80, 11.5); }

        Craft makeNuggets() {
            return new Craft(nugget(), nugget(), kind + "_alloy_ingot", 1, nugget(), 9);
        }

        Craft makeCrystal() {
            return Craft.shaped(crystal(), 1, "NNNNGNNNN", Map.of('N', nugget(), 'G', gem));
        }
    }

    private static final BlockPos CHEST = new BlockPos(3403, 80, 8);
    private static final BlockPos TABLE = new BlockPos(3402, 80, 8);
    private static final Vec3 WORKSHOP = new Vec3(3402.5, 80, 11.5);
    private static final Route PULSATING = new Route("pulsating", "minecraft:diamond", 3406);
    private static final Route VIBRANT = new Route("vibrant", "minecraft:emerald", 3409);
    private static final List<Route> ROUTES = List.of(PULSATING, VIBRANT);
    private static final List<Craft> RECIPES = List.of(PULSATING.makeNuggets(), PULSATING.makeCrystal(),
        VIBRANT.makeNuggets(), VIBRANT.makeCrystal());

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int recipeIndex;
    private int millIndex;
    private int actionAt = -1;
    private Map<String, Integer> inputsBefore = Map.of();

    ClientCrystalProgressionSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 165_000_000_000L) {
                throw new AssertionError("Crystals stopped at " + stage + ", recipe " + recipeIndex + ", mill " + millIndex);
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
                    check("native_workshop_chest", open(CHEST));
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)) return;
                    var menu = screen.getMenu();
                    if (menu.slots.subList(0, 27).stream().filter(Slot::hasItem).count() != 4) return;
                    for (int slot = 0; slot < 4; slot++) quickMove(menu, slot);
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)
                        || screen.getMenu().slots.subList(0, 27).stream().anyMatch(Slot::hasItem)) return;
                    check("the_original_two_alloys_and_both_gems_enter_the_survival_inventory",
                        count("pulsating_alloy_ingot") == 2 && count("vibrant_alloy_ingot") == 8
                            && count(PULSATING.gem()) == 1 && count(VIBRANT.gem()) == 1);
                    check("both_crystal_recipes_require_the_nugget_discovery", ROUTES.stream().noneMatch(route ->
                        player.getRecipeBook().contains(minecraft.level.getRecipeManager().byKey(id(route.crystal())).orElseThrow())));
                    player.closeContainer();
                    check("native_crafting_table", open(TABLE));
                    stage = Stage.RECIPE;
                }
                case RECIPE, RESULT, COLLECT -> craft(RECIPES.get(recipeIndex));
                case MILL, INSERT, INSERTED, PAID -> mill(ROUTES.get(millIndex));
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

    private void craft(Craft craft) throws IOException {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        var grid = menu.slots.subList(1, 10);
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(id(craft.recipe())).orElseThrow();
                if (!minecraft.player.getRecipeBook().contains(recipe)) return;
                inputsBefore = new LinkedHashMap<>();
                for (String ingredient : craft.inputs().keySet()) inputsBefore.put(ingredient, count(ingredient));
                check(craft.name() + "_naturally_unlocks_with_the_original_ingredients", count(craft.result()) == 0
                    && grid.stream().noneMatch(Slot::hasItem) && craft.inputs().entrySet().stream().allMatch(entry ->
                    inputsBefore.get(entry.getKey()) >= entry.getValue()));
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                var stack = menu.getSlot(0).getItem();
                if (!stack.is(item(craft.result())) || grid.stream().filter(Slot::hasItem).count() != craft.ingredientCount()) return;
                check(craft.name() + "_uses_the_stock_layout_and_output_count", craft.matchesGrid(grid) && stack.getCount() == craft.results());
                if (craft.result().endsWith("_crystal")) capture(craft.result() + "-recipe");
                quickMove(menu, 0);
                stage = Stage.COLLECT;
            }
            case COLLECT -> {
                if (grid.stream().anyMatch(Slot::hasItem) || count(craft.result()) != craft.results()) return;
                check(craft.name() + "_uses_exactly_the_stock_ingredients", !menu.getSlot(0).hasItem() && menu.getCarried().isEmpty()
                    && craft.inputs().entrySet().stream().allMatch(entry ->
                    count(entry.getKey()) == inputsBefore.get(entry.getKey()) - entry.getValue()));
                if (++recipeIndex == RECIPES.size()) {
                    minecraft.player.closeContainer();
                    signal("crafted");
                    stage = Stage.MILL;
                } else stage = Stage.RECIPE;
            }
            default -> {}
        }
    }

    private void mill(Route route) throws IOException {
        if (stage == Stage.MILL) {
            if (!settled(route.kind(), route.stand())) return;
            check(route.kind() + "_native_sag_mill_menu", open(route.mill()));
            stage = Stage.INSERT;
            return;
        }
        if (!(minecraft.screen instanceof SagMillScreen screen)) return;
        var player = minecraft.player;
        var menu = screen.getMenu();
        switch (stage) {
            case INSERT -> {
                var slot = menu.slots.stream().filter(candidate -> candidate.container == player.getInventory()
                    && candidate.getItem().is(item(route.crystal()))).findFirst();
                if (slot.isEmpty()) return;
                quickMove(menu, slot.get().index);
                stage = Stage.INSERTED;
            }
            case INSERTED -> {
                var input = menu.getSlot(SagMillMenu.INPUTS_INDEX).getItem();
                if (!input.is(item(route.crystal())) || count(route.crystal()) != 0) return;
                check(route.kind() + "_native_transfer_inserts_one_original_crystal", input.getCount() == 1);
                signal("inserted-" + route.kind());
                stage = Stage.PAID;
            }
            case PAID -> {
                if (!ClientSagProgressionSmoke.matches(menu, GrindingBallData.IDENTITY, 600, 2400, 0)) return;
                var flint = menu.getSlot(SagMillMenu.LAST_INDEX).getItem();
                var input = menu.getSlot(SagMillMenu.INPUTS_INDEX).getItem();
                check(route.kind() + "_menu_syncs_one_quarter_progress_with_unused_flint",
                    flint.is(Items.FLINT) && flint.getCount() == 1 && input.is(item(route.crystal())) && input.getCount() == 1);
                capture(route.kind() + "-paused-mill");
                player.closeContainer();
                signal("paused-" + route.kind());
                stage = ++millIndex == ROUTES.size() ? Stage.VERIFY : Stage.MILL;
            }
            default -> {}
        }
    }

    private int count(String name) {
        return minecraft.player.getInventory().countItem(item(name));
    }

    private boolean open(BlockPos pos) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
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
            new ServerData("Ender IO Crystal Progression", address, true), false);
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
        ClientSmoke.complete("client_crystal_progression", result);
    }
}
