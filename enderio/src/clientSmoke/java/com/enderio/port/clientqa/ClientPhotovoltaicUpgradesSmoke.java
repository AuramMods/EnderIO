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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
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

final class ClientPhotovoltaicUpgradesSmoke {
    private enum Stage { TITLE, JOIN, MINE, MINING, PICKUP, CHEST, TAKE, TABLE, RECIPE, RESULT, STORE, COLLECT,
        PLACE, CROUCH_PLACE, PLACED, VERIFY, DONE }

    private record Upgrade(String name, String from, Craft recipe) {}

    private static final BlockPos CHEST = new BlockPos(3569, 80, 8);
    private static final BlockPos TABLE = new BlockPos(3568, 80, 8);
    private static final BlockPos PANEL = new BlockPos(3574, 80, 8);
    private static final Vec3 WORKSHOP = new Vec3(3568.5, 80, 11.5);
    private static final Vec3 PANEL_STAND = new Vec3(3574.5, 80, 11.5);
    private static final String ENERGETIC = "energetic_photovoltaic_module";
    private static final String PULSATING = "pulsating_photovoltaic_module";
    private static final String VIBRANT = "vibrant_photovoltaic_module";
    private static final List<Upgrade> UPGRADES = List.of(
        new Upgrade("pulsating", ENERGETIC, Craft.shaped(PULSATING, 1, "IFIPDPCSC", Map.of(
            'I', "pulsating_alloy_ingot", 'F', "fused_quartz_e", 'D', "powdered_coal",
            'P', "photovoltaic_plate", 'C', "double_layer_capacitor", 'S', ENERGETIC))),
        new Upgrade("vibrant", PULSATING, Craft.shaped(VIBRANT, 1, "IFIPGPCSC", Map.of(
            'I', "vibrant_alloy_ingot", 'F', "fused_quartz_d", 'G', "minecraft:glowstone",
            'P', "photovoltaic_plate", 'C', "octadic_capacitor", 'S', PULSATING))));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int actionAt = -1;
    private int miningTick;
    private int index;

    ClientPhotovoltaicUpgradesSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 150_000_000_000L) {
                String screen = minecraft.screen == null ? "world" : minecraft.screen.getClass().getSimpleName();
                throw new AssertionError("Photovoltaic upgrades stopped at " + stage + ", upgrade " + index + ", screen " + screen);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            var upgrade = UPGRADES.get(index);
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_tcp", !player.isCreative() && !player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    check("the_copied_player_has_yet_to_unlock_vibrant", !knows(UPGRADES.get(1).recipe()));
                    signal("connected");
                    stage = Stage.MINE;
                }
                case MINE, MINING, PICKUP -> mine(upgrade);
                case CHEST -> {
                    if (!settled("recipe-" + upgrade.name(), WORKSHOP)) return;
                    check(upgrade.name() + "_native_workshop_chest", use(CHEST, Direction.SOUTH));
                    stage = Stage.TAKE;
                }
                case TAKE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)) return;
                    var menu = screen.getMenu();
                    if (menu.slots.subList(0, 27).stream().filter(Slot::hasItem).count() != 5) return;
                    for (int slot = 0; slot < 5; slot++) click(menu, slot, ClickType.QUICK_MOVE);
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)
                        || screen.getMenu().slots.subList(0, 27).stream().anyMatch(Slot::hasItem)) return;
                    player.closeContainer();
                    check(upgrade.name() + "_native_crafting_table", use(TABLE, Direction.SOUTH));
                    stage = Stage.RECIPE;
                }
                case RECIPE, RESULT, STORE, COLLECT -> craft(upgrade);
                case PLACE -> {
                    if (!settled("place-" + upgrade.name(), PANEL_STAND) || !equip(item(upgrade.recipe().result()))) return;
                    minecraft.options.keyShift.setDown(true);
                    stage = Stage.CROUCH_PLACE;
                }
                case CROUCH_PLACE -> {
                    if (!player.isCrouching()) return;
                    check(upgrade.name() + "_native_survival_placement", use(PANEL.below(), Direction.UP));
                    minecraft.options.keyShift.setDown(false);
                    stage = Stage.PLACED;
                }
                case PLACED -> {
                    if (!(minecraft.level.getBlockEntity(PANEL) instanceof SolarPanelBlockEntity)
                        || !minecraft.level.getBlockState(PANEL).is(Block.byItem(item(upgrade.recipe().result())))
                        || count(upgrade.recipe().result()) != 0) return;
                    capture(upgrade.name() + "-original-module-placed");
                    signal("placed-" + upgrade.name());
                    if (index + 1 == UPGRADES.size()) stage = Stage.VERIFY;
                    else {
                        index++;
                        stage = Stage.MINE;
                    }
                }
                case VERIFY -> {
                    if (!ready("done")) return;
                    var axes = player.getInventory().items.stream().filter(stack -> stack.is(Items.IRON_PICKAXE)).toList();
                    check("two_native_recoveries_use_two_pickaxe_durability", axes.size() == 1 && axes.get(0).getDamageValue() == 2);
                    finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void mine(Upgrade upgrade) throws IOException {
        var player = minecraft.player;
        switch (stage) {
            case MINE -> {
                if (!settled("mine-" + upgrade.name(), PANEL_STAND) || !hasPickaxe()) return;
                check(upgrade.name() + "_mines_the_actual_previous_tier_in_survival",
                    minecraft.level.getBlockState(PANEL).is(Block.byItem(item(upgrade.from())))
                        && minecraft.gameMode.startDestroyBlock(PANEL, Direction.SOUTH));
                miningTick = player.tickCount;
                stage = Stage.MINING;
            }
            case MINING -> {
                if (!minecraft.level.getBlockState(PANEL).isAir()) {
                    if (player.tickCount != miningTick) {
                        minecraft.gameMode.continueDestroyBlock(PANEL, Direction.SOUTH);
                        miningTick = player.tickCount;
                    }
                    return;
                }
                signal("mined-" + upgrade.name());
                stage = Stage.PICKUP;
            }
            case PICKUP -> {
                if (count(upgrade.from()) != 1) return;
                signal("picked-" + upgrade.name());
                stage = Stage.CHEST;
            }
            default -> {}
        }
    }

    private boolean hasPickaxe() {
        return minecraft.player.getInventory().countItem(Items.IRON_PICKAXE) == 1 && equip(Items.IRON_PICKAXE);
    }

    private void craft(Upgrade upgrade) throws IOException {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        var grid = menu.slots.subList(1, 10);
        var step = upgrade.recipe();
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(id(step.recipe())).orElseThrow();
                if (!minecraft.player.getRecipeBook().contains(recipe)) return;
                check(upgrade.name() + "_book_accepts_the_recovered_panel_and_exact_stock_parts", grid.stream().noneMatch(Slot::hasItem)
                    && count(step.result()) == 0 && step.inputs().entrySet().stream().allMatch(entry -> count(entry.getKey()) == entry.getValue()));
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                var stack = menu.getSlot(0).getItem();
                if (!stack.is(item(step.result())) || grid.stream().filter(Slot::hasItem).count() != step.ingredientCount()) return;
                check(upgrade.name() + "_stock_recipe_returns_one_module", step.matchesGrid(grid) && stack.getCount() == 1);
                capture(upgrade.name() + "-upgrade-recipe");
                click(menu, 0, ClickType.PICKUP);
                stage = Stage.STORE;
            }
            case STORE -> {
                var carried = menu.getCarried();
                if (!carried.is(item(step.result())) || carried.getCount() != 1) return;
                var inventory = minecraft.player.getInventory();
                var target = menu.slots.stream().filter(slot -> slot.container == inventory && !slot.hasItem()).findFirst().orElseThrow();
                click(menu, target.index, ClickType.PICKUP);
                stage = Stage.COLLECT;
            }
            case COLLECT -> {
                if (grid.stream().anyMatch(Slot::hasItem) || count(step.result()) != 1 || !menu.getCarried().isEmpty()
                    || menu.getSlot(0).hasItem() || step.inputs().keySet().stream().anyMatch(input -> count(input) != 0)) return;
                check(upgrade.name() + "_consumes_the_recovered_module_and_all_stock_parts", true);
                minecraft.player.closeContainer();
                signal("crafted-" + upgrade.name());
                stage = Stage.PLACE;
            }
            default -> {}
        }
    }

    private boolean equip(Item item) {
        var player = minecraft.player;
        var inventory = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            if (!inventory.getItem(slot).is(item)) continue;
            if (slot < 9) {
                inventory.selected = slot;
                return true;
            }
            click(player.inventoryMenu, slot, ClickType.SWAP);
            return false;
        }
        return false;
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
            new ServerData("Ender IO Photovoltaic Upgrades", address, true), false);
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
        minecraft.options.keyShift.setDown(false);
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_photovoltaic_upgrades", result);
    }
}
