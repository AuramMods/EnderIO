package com.enderio.port.clientqa;

import com.enderio.enderio.api.io.RedstoneControl;
import com.enderio.enderio.client.content.machines.gui.screen.ObeliskScreen;
import com.enderio.enderio.client.foundation.widgets.RedstoneControlPickerWidget;
import com.enderio.enderio.content.machines.obelisks.attractor.AttractorObeliskMenu;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOItems;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.Craft;
import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.id;
import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.item;

final class ClientAttractorProgressionSmoke {
    private enum Stage { TITLE, JOIN, CHEST, TAKE, TABLE, RECIPE, RESULT, COLLECT, PLACE, PLACED,
        OPEN, INSERT, SLOTS, CONFIGURED, VERIFY, DONE }

    private static final BlockPos CHEST = new BlockPos(3525, 80, 8);
    private static final BlockPos TABLE = new BlockPos(3524, 80, 8);
    private static final BlockPos OBELISK = new BlockPos(3528, 80, 8);
    private static final Vec3 WORKSHOP = new Vec3(3524.5, 80, 11.5);
    private static final Vec3 STAND = new Vec3(3528.5, 80, 11.5);
    private static final Craft RECIPE = Craft.shaped("attractor_obelisk", 1, " M EGEICI", Map.of(
        'M', "enticing_crystal", 'E', "energetic_alloy_ingot", 'G', "energized_gear",
        'I', "soularium_ingot", 'C', "ensouled_chassis"));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int actionAt = -1;

    ClientAttractorProgressionSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 162_000_000_000L) {
                throw new AssertionError("Attractor progression stopped at " + stage);
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
                    if (menu.slots.subList(0, 27).stream().filter(Slot::hasItem).count() != 7) return;
                    var recipe = minecraft.level.getRecipeManager().byKey(id(RECIPE.recipe())).orElseThrow();
                    check("the_obelisk_recipe_waits_for_the_saved_enticing_crystal", !player.getRecipeBook().contains(recipe)
                        && count("enticing_crystal") == 0 && menu.getSlot(0).getItem().is(item("enticing_crystal")));
                    for (int slot = 0; slot < 7; slot++) quickMove(menu, slot);
                    stage = Stage.TABLE;
                }
                case TABLE -> {
                    if (!(minecraft.screen instanceof ContainerScreen screen)
                        || screen.getMenu().slots.subList(0, 27).stream().anyMatch(Slot::hasItem)) return;
                    player.closeContainer();
                    check("native_crafting_table", use(TABLE, Direction.SOUTH));
                    stage = Stage.RECIPE;
                }
                case RECIPE, RESULT, COLLECT -> craft();
                case PLACE -> {
                    if (!settled("place", STAND)) return;
                    int slot = player.getInventory().findSlotMatchingItem(new ItemStack(EIOBlocks.ATTRACTOR_OBELISK.asItem()));
                    if (slot < 0) throw new AssertionError("Missing crafted Attractor Obelisk");
                    if (slot >= 9) {
                        minecraft.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, slot, 0, ClickType.SWAP, player);
                        return;
                    }
                    player.getInventory().selected = slot;
                    check("native_placement_consumes_the_original_crafted_obelisk", use(OBELISK.below(), Direction.UP));
                    stage = Stage.PLACED;
                }
                case PLACED -> {
                    if (!minecraft.level.getBlockState(OBELISK).is(EIOBlocks.ATTRACTOR_OBELISK.get()) || count(RECIPE.result()) != 0) return;
                    signal("placed");
                    stage = Stage.OPEN;
                }
                case OPEN -> {
                    if (!settled("open", STAND)) return;
                    check("the_player_opens_the_attractor_it_just_placed", use(OBELISK, Direction.SOUTH));
                    stage = Stage.INSERT;
                }
                case INSERT, SLOTS, CONFIGURED, VERIFY -> configure();
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craft() throws IOException {
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        var grid = menu.slots.subList(1, 10);
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(id(RECIPE.recipe())).orElseThrow();
                if (!minecraft.player.getRecipeBook().contains(recipe)) return;
                check("the_original_crystal_unlocks_the_recipe_with_exact_ingredients", count(RECIPE.result()) == 0
                    && grid.stream().noneMatch(Slot::hasItem) && RECIPE.inputs().entrySet().stream().allMatch(entry ->
                    count(entry.getKey()) == entry.getValue()));
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                var stack = menu.getSlot(0).getItem();
                if (!stack.is(item(RECIPE.result())) || grid.stream().filter(Slot::hasItem).count() != RECIPE.ingredientCount()) return;
                check("stock_crafting_grid_produces_one_attractor", RECIPE.matchesGrid(grid) && stack.getCount() == 1);
                capture("attractor-recipe");
                quickMove(menu, 0);
                stage = Stage.COLLECT;
            }
            case COLLECT -> {
                if (grid.stream().anyMatch(Slot::hasItem) || count(RECIPE.result()) != 1) return;
                check("native_crafting_spends_the_original_ingredients_once", !menu.getSlot(0).hasItem()
                    && menu.getCarried().isEmpty() && RECIPE.inputs().keySet().stream().allMatch(input -> count(input) == 0));
                minecraft.player.closeContainer();
                signal("crafted");
                stage = Stage.PLACE;
            }
            default -> {}
        }
    }

    private void configure() throws IOException {
        if (!(minecraft.screen instanceof ObeliskScreen<?, ?> screen)
            || !(screen.getMenu() instanceof AttractorObeliskMenu menu)) return;
        switch (stage) {
            case INSERT -> {
                int capacitor = playerSlot(menu, EIOItems.BASIC_CAPACITOR.get());
                int filter = playerSlot(menu, EIOItems.BASIC_SOUL_FILTER.get());
                if (capacitor < 0 || filter < 0) return;
                check("the_crafted_obelisk_opens_with_empty_machine_slots", !menu.getSlot(0).hasItem() && !menu.getSlot(1).hasItem());
                quickMove(menu, capacitor);
                quickMove(menu, filter);
                stage = Stage.SLOTS;
            }
            case SLOTS -> {
                if (!menu.isCapacitorInstalled() || !ClientEntityObeliskSmoke.validFilter(menu)
                    || menu.getMaxRange() != 8 || menu.getBlockEntity().getRange() != 8) return;
                check("native_slots_accept_one_capacitor_and_the_pig_only_filter", menu.getSlot(0).getItem().is(EIOItems.BASIC_CAPACITOR.get())
                    && playerSlot(menu, EIOItems.BASIC_CAPACITOR.get()) < 0 && playerSlot(menu, EIOItems.BASIC_SOUL_FILTER.get()) < 0);
                check("fresh_attractor_starts_hidden_and_always_active", !menu.isRangeVisible()
                    && menu.getRedstoneControl() == RedstoneControl.ALWAYS_ACTIVE);
                check("range_decrease_widget_responds", screen.mouseClicked(screen.getGuiLeft() + screen.getXSize() - 29, screen.getGuiTop() + 45, 0));
                check("range_visibility_widget_responds", screen.mouseClicked(screen.getGuiLeft() + screen.getXSize() - 14, screen.getGuiTop() + 42, 0));
                var picker = screen.children().stream().filter(RedstoneControlPickerWidget.class::isInstance)
                    .map(RedstoneControlPickerWidget.class::cast).findFirst().orElseThrow();
                check("redstone_picker_responds", picker.mouseScrolled(picker.getX() + 8, picker.getY() + 8, -1));
                stage = Stage.CONFIGURED;
            }
            case CONFIGURED -> {
                if (!menu.isRangeVisible() || menu.getBlockEntity().getRange() != 7
                    || menu.getRedstoneControl() != RedstoneControl.ACTIVE_WITH_SIGNAL) return;
                check("the_attractor_syncs_the_configured_range_filter_and_upkeep", ClientEntityObeliskSmoke.validFilter(menu)
                    && menu.getBlockEntity().getPerTickEnergyCost() == 18);
                capture("configured-attractor");
                signal("configured");
                stage = Stage.VERIFY;
            }
            case VERIFY -> {
                if (!ready("done") || menu.getEnergyStorage() == null || menu.getEnergyStorage().energyStored() <= 0) return;
                check("the_real_menu_retains_range_visibility_and_signal_mode_after_attracting_the_pig",
                    menu.getBlockEntity().getRange() == 7 && menu.isRangeVisible()
                        && menu.getRedstoneControl() == RedstoneControl.ACTIVE_WITH_SIGNAL && ClientEntityObeliskSmoke.validFilter(menu));
                capture("attractor-after-pull");
                finish(null);
            }
            default -> {}
        }
    }

    private int playerSlot(AbstractContainerMenu menu, Item item) {
        var inventory = minecraft.player.getInventory();
        return menu.slots.stream().filter(slot -> slot.container == inventory && slot.getItem().is(item))
            .findFirst().map(slot -> slot.index).orElse(-1);
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
            new ServerData("Ender IO Attractor Progression", address, true), false);
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
        ClientSmoke.complete("client_attractor_progression", result);
    }
}
