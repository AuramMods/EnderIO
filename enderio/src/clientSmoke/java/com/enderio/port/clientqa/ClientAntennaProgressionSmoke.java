package com.enderio.port.clientqa;

import com.enderio.core.common.energy.ItemStackEnergy;
import com.enderio.enderio.client.content.machines.gui.screen.WirelessChargerScreen;
import com.enderio.enderio.content.machines.wireless_charger.WirelessChargerMenu;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
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

final class ClientAntennaProgressionSmoke {
    private enum Stage { TITLE, JOIN, TABLE, RECIPE, RESULT, COLLECT, PLACE, OPEN, MENU, BREAK, MINING, VERIFY, DONE }

    private record Assembly(String name, String block, BlockPos pos, Direction face, int range) {}

    private static final String CHARGER = "wireless_charger";
    private static final String STANDARD = "wireless_charger_antenna";
    private static final String ADVANCED = "wireless_charger_antenna_advanced";
    private static final String CONDUCTIVE = "conductive_alloy_ingot";
    private static final BlockPos BASE = new BlockPos(3160, 80, 23);
    private static final BlockPos TABLE = new BlockPos(3159, 80, 25);
    private static final Vec3 WORKBENCH = new Vec3(3159.5, 80, 27.5);
    private static final Vec3 STAND = new Vec3(3160.5, 80, 25.5);
    private static final List<Assembly> ASSEMBLIES = List.of(
        new Assembly("base", CHARGER, BASE, Direction.UP, 16),
        new Assembly("standard", STANDARD, BASE.above(), Direction.WEST, 32),
        new Assembly("advanced", ADVANCED, BASE.above(), Direction.WEST, 48));
    private static final List<Craft> RECIPES = List.of(
        Craft.shaped(CHARGER, 1, "CCCCVCCEC", Map.of('C', CONDUCTIVE, 'V', "void_chassis", 'E', "ender_resonator")),
        Craft.shaped(STANDARD, 1, " S  S CEC", Map.of('C', CONDUCTIVE, 'S', "pulsating_alloy_ingot", 'E', "ender_resonator")),
        Craft.shaped(ADVANCED, 1, " S  S CEC", Map.of('C', CONDUCTIVE, 'S', "vibrant_alloy_ingot", 'E', "sentient_ender")));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final boolean restarted = "verify".equals(System.getProperty("enderio.clientqa.multiplayerPhase"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int recipeIndex;
    private int assemblyIndex;
    private int actionAt = -1;
    private int miningTick;
    private int unlockBy;
    private int menuSyncBy;
    private boolean installingCapacitor;
    private Map<String, Integer> inputsBefore = Map.of();

    ClientAntennaProgressionSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 183_000_000_000L) {
                throw new AssertionError("Antenna progression stopped at " + stage + ", recipe " + recipeIndex + ", assembly " + assemblyIndex);
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
                    if (restarted) {
                        assemblyIndex = ASSEMBLIES.size() - 1;
                        stage = Stage.OPEN;
                    } else stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (!settled("craft", WORKBENCH)) return;
                    check("survival_keeps_the_original_sentient_ender_before_crafting", count(item("sentient_ender")) == 1);
                    use(TABLE, Direction.SOUTH);
                    unlockBy = player.tickCount + 120;
                    stage = Stage.RECIPE;
                }
                case RECIPE, RESULT, COLLECT -> craft(RECIPES.get(recipeIndex));
                case PLACE -> place(ASSEMBLIES.get(assemblyIndex));
                case OPEN -> {
                    var assembly = ASSEMBLIES.get(assemblyIndex);
                    if (!settled("place-" + assembly.name(), STAND) || !placed(assembly)) return;
                    check(assembly.name() + "_block_and_placement_consumption",
                        count(item(assembly.block())) == 0 && use(BASE, Direction.SOUTH));
                    menuSyncBy = player.tickCount + 80;
                    stage = Stage.MENU;
                }
                case MENU -> inspect(ASSEMBLIES.get(assemblyIndex));
                case BREAK -> {
                    if (!settled("remove-standard", STAND) || !equip(Items.IRON_PICKAXE)) return;
                    check("native_survival_mines_the_crafted_standard_antenna",
                        minecraft.gameMode.startDestroyBlock(BASE.above(), Direction.SOUTH));
                    miningTick = player.tickCount;
                    stage = Stage.MINING;
                }
                case MINING -> {
                    if (!minecraft.level.getBlockState(BASE.above()).isAir()) {
                        if (player.tickCount != miningTick) {
                            minecraft.gameMode.continueDestroyBlock(BASE.above(), Direction.SOUTH);
                            miningTick = player.tickCount;
                        }
                        return;
                    }
                    if (player.getMainHandItem().getDamageValue() != 1) return;
                    check("native_mining_costs_one_pickaxe_durability", player.getMainHandItem().is(Items.IRON_PICKAXE));
                    signal("removed-standard");
                    assemblyIndex++;
                    stage = Stage.PLACE;
                }
                case VERIFY -> {
                    if (!ready("done")) return;
                    var staff = player.getInventory().getItem(0);
                    check("the_original_staff_receives_wireless_charge", staff.is(EIOItems.TRAVEL_STAFF.get())
                        && staff.hasTag() && staff.getTag().getInt("PortAntenna") == 187 && ItemStackEnergy.getEnergyStored(staff) > 0);
                    finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Antenna Progression", address, true), false);
        stage = Stage.JOIN;
    }

    private void craft(Craft step) throws IOException {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var player = minecraft.player;
        var menu = screen.getMenu();
        var grid = menu.slots.subList(1, 10);
        var result = item(step.result());
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(id(step.recipe())).orElseThrow();
                if (!player.getRecipeBook().contains(recipe)) {
                    if (player.tickCount >= unlockBy) throw new AssertionError("Recipe stayed locked with ingredients " + step.recipe());
                    return;
                }
                inputsBefore = new LinkedHashMap<>();
                for (String ingredient : step.inputs().keySet()) inputsBefore.put(ingredient, count(item(ingredient)));
                check(step.name() + "_naturally_unlocked", count(result) == 0 && grid.stream().noneMatch(Slot::hasItem)
                    && step.inputs().entrySet().stream().allMatch(entry -> inputsBefore.get(entry.getKey()) >= entry.getValue()));
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                var stack = menu.getSlot(0).getItem();
                if (!stack.is(result) || grid.stream().filter(Slot::hasItem).count() != step.ingredientCount()) return;
                check(step.name() + "_has_the_stock_grid_and_single_output", stack.getCount() == 1 && step.matchesGrid(grid));
                if (step.result().equals(ADVANCED)) capture("advanced-antenna-grid");
                quickMove(menu, 0);
                stage = Stage.COLLECT;
            }
            case COLLECT -> {
                if (grid.stream().anyMatch(Slot::hasItem) || count(result) != 1) return;
                check(step.name() + "_spends_the_exact_ingredients", step.inputs().entrySet().stream().allMatch(entry ->
                    count(item(entry.getKey())) == inputsBefore.get(entry.getKey()) - entry.getValue()));
                if (++recipeIndex == RECIPES.size()) {
                    player.closeContainer();
                    signal("crafted");
                    stage = Stage.PLACE;
                } else {
                    unlockBy = player.tickCount + 120;
                    stage = Stage.RECIPE;
                }
            }
            default -> {}
        }
    }

    private void place(Assembly assembly) {
        if (!settled("place-" + assembly.name(), STAND) || !equip(item(assembly.block()))) return;
        BlockPos support = assembly.pos().relative(assembly.face().getOpposite());
        check(assembly.name() + "_native_survival_placement", use(support, assembly.face()));
        stage = Stage.OPEN;
    }

    private void inspect(Assembly assembly) throws IOException {
        if (!(minecraft.screen instanceof WirelessChargerScreen screen)) return;
        var player = minecraft.player;
        WirelessChargerMenu menu = screen.getMenu();
        if (assembly.name().equals("base") && !menu.isCapacitorInstalled() && !installingCapacitor) {
            var capacitor = menu.slots.stream().filter(slot -> slot.container == player.getInventory()
                && slot.getItem().is(EIOItems.BASIC_CAPACITOR.get())).findFirst();
            if (capacitor.isPresent()) {
                quickMove(menu, capacitor.get().index);
                installingCapacitor = true;
                menuSyncBy = player.tickCount + 80;
            }
        }
        int capacity = menu.getEnergyStorage() == null ? -1 : menu.getEnergyStorage().maxEnergyStored();
        boolean synchronizedMenu = menu.isCapacitorInstalled() && menu.getSlot(0).getItem().is(EIOItems.BASIC_CAPACITOR.get())
            && count(EIOItems.BASIC_CAPACITOR.get()) == 0 && capacity == 128000
            && menu.getMaxRange() == assembly.range() && menu.getBlockEntity().getRange() == assembly.range();
        if (!synchronizedMenu && player.tickCount < menuSyncBy) return;
        if (!synchronizedMenu) throw new AssertionError(assembly.name() + " menu failed to sync: capacity " + capacity
            + ", range " + menu.getBlockEntity().getRange() + ", maximum " + menu.getMaxRange()
            + ", slot " + menu.getSlot(0).getItem() + ", spare capacitors " + count(EIOItems.BASIC_CAPACITOR.get()));
        check(assembly.name() + "_menu_syncs_the_capacitor_capacity_and_stock_range", synchronizedMenu);
        capture(assembly.name() + "-charger-menu");
        minecraft.player.closeContainer();
        signal("checked-" + assembly.name());
        if (assembly.name().equals("standard")) stage = Stage.BREAK;
        else if (assembly.name().equals("advanced")) stage = Stage.VERIFY;
        else {
            assemblyIndex++;
            stage = Stage.PLACE;
        }
    }

    private boolean placed(Assembly assembly) {
        return minecraft.level.getBlockState(assembly.pos()).is(BuiltInRegistries.BLOCK.getOptional(id(assembly.block())).orElseThrow());
    }

    private boolean settled(String marker, Vec3 position) {
        var player = minecraft.player;
        if (!ready(marker) || minecraft.screen != null || player.distanceToSqr(position) > 0.15) {
            actionAt = -1;
            return false;
        }
        if (actionAt < 0) actionAt = player.tickCount + 8;
        if (player.tickCount < actionAt) return false;
        actionAt = -1;
        return true;
    }

    private boolean equip(Item item) {
        var player = minecraft.player;
        var inventory = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (!inventory.getItem(slot).is(item)) continue;
            inventory.selected = slot;
            return true;
        }
        for (int slot = 9; slot < 36; slot++) {
            if (!inventory.getItem(slot).is(item)) continue;
            minecraft.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, slot, 2, ClickType.SWAP, player);
            return false;
        }
        throw new AssertionError("Missing crafted item " + item);
    }

    private boolean use(BlockPos pos, Direction face) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
        return minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction();
    }

    private int count(Item item) {
        return minecraft.player.getInventory().countItem(item);
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
        check(name + "_quiet_hidden_frame", Boolean.TRUE.equals(frame.get("passed")));
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
        ClientSmoke.complete("client_antenna_progression", result);
    }
}
