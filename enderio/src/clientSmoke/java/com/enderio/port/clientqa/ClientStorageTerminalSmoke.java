package com.enderio.port.clientqa;

import appeng.api.stacks.AEItemKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
import appeng.client.gui.widgets.OpenGuideButton;
import com.refinedmods.refinedstorage.screen.grid.GridScreen;
import com.refinedmods.refinedstorage.screen.grid.stack.ItemGridStack;
import com.enderio.enderio.api.EnderIODataComponents;
import com.enderio.enderio.content.conduits.bundle.ConduitBundleBlockEntity;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientStorageTerminalSmoke {
    private enum Stage {
        TITLE, JOIN, TABLE, RECIPE, RESULT, TAKEN, PLACE_CONDUIT, PLACED_CONDUIT,
        OPEN, PLACING_GRID, INITIAL, GUIDE_PAGE, GUIDE_SCROLLED, PICKUP, DEPOSITED, WITHDRAWING, STORING, VERIFIED, DONE
    }

    private record Route(String name, int marker, int destination) {
        BlockPos pos() { return new BlockPos(1085, 80, marker); }
    }

    private record Entry(long quantity, double x, double y, int index) { }

    private static final Route AE = new Route("ae", 4, 1);
    private static final Route RS = new Route("rs", 13, 2);
    private static final BlockPos TABLE = new BlockPos(1084, 80, 10);
    private static final BlockPos RS_HEAD = new BlockPos(1087, 80, 13);
    private static final ResourceLocation RS_CONDUIT = ResourceLocation.parse("enderio:rs");
    private static final ResourceLocation RS_RECIPE = ResourceLocation.parse("enderio:rs_conduit");
    private static final ResourceLocation GRID_ITEM = ResourceLocation.fromNamespaceAndPath("refinedstorage", "grid");
    private static final ResourceLocation TERMINAL_GUIDE = ResourceLocation.parse("ae2:items-blocks-machines/terminals.md");
    private static final int BEFORE = 37;
    private static final int OFFER = 5;
    private static final int HALF = (BEFORE + OFFER) / 2;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final boolean reopen = System.getProperty("enderio.clientqa.multiplayerPhase").equals("verify");
    private final boolean stock;
    private final boolean guide;
    private final int initial;
    private final int offer;
    private final int offerSlot;
    private final int withdrawal;
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final Map<String, Object> guideObservation = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private long titleAt;
    private int arrivalTick = -1;
    private int placedConduits;
    private int guideTick = -1;
    private int originalScroll;
    private AbstractContainerScreen<?> terminalBeforeGuide;
    private Stage stage = Stage.TITLE;
    private Route route;

    ClientStorageTerminalSmoke(boolean stock, boolean guide) {
        this.stock = stock;
        this.guide = guide;
        route = stock ? RS : AE;
        initial = reopen ? HALF : BEFORE;
        offer = stock && reopen ? 3 : OFFER;
        offerSlot = stock ? 5 : 0;
        withdrawal = (initial + offer) / 2;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen screen) {
                throw new AssertionError("Disconnected during " + route.name + " " + stage + " with " + screen.getNarrationMessage().getString());
            }
            if (System.nanoTime() - started > 150_000_000_000L) {
                throw new AssertionError("Timed out during " + route.name + " " + stage);
            }
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleAt == 0) titleAt = System.nanoTime();
                if (System.nanoTime() - titleAt < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Storage conduit QA", address, true), false);
                stage = Stage.JOIN;
                return;
            }
            if (minecraft.player == null || minecraft.level == null) return;
            switch (stage) {
                case JOIN -> {
                    if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                        SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    check("dedicated_survival_connection", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !minecraft.getConnection().getConnection().isMemoryConnection());
                    signal("connected");
                    stage = stock && !reopen ? Stage.TABLE : Stage.OPEN;
                }
                case TABLE, RECIPE, RESULT, TAKEN -> craftConduits();
                case PLACE_CONDUIT, PLACED_CONDUIT -> placeConduits();
                case OPEN -> open();
                case PLACING_GRID -> {
                    if (minecraft.player.getInventory().getItem(8).isEmpty() && !minecraft.level.getBlockState(RS.pos()).isAir()) {
                        signal("rs-placed");
                        stage = Stage.OPEN;
                    }
                }
                case INITIAL, PICKUP, DEPOSITED, WITHDRAWING, STORING -> useTerminal();
                case GUIDE_PAGE, GUIDE_SCROLLED -> verifyGuide();
                case VERIFIED -> {
                    if (ready("server-verified")) finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void craftConduits() throws IOException {
        var player = minecraft.player;
        if (stage == Stage.TABLE) {
            if (!ready("server-table") || minecraft.screen != null || player.distanceToSqr(1084.5, 80, 12.5) > 0.15) return;
            player.getInventory().selected = 8;
            var hit = new BlockHitResult(Vec3.atCenterOf(TABLE).relative(Direction.SOUTH, 0.5), Direction.SOUTH, TABLE, false);
            check("native_crafting_table_opens", minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
            stage = Stage.RECIPE;
            return;
        }
        if (!(minecraft.screen instanceof CraftingScreen screen)) return;
        var menu = screen.getMenu();
        switch (stage) {
            case RECIPE -> {
                var recipe = minecraft.level.getRecipeManager().byKey(RS_RECIPE).orElseThrow();
                if (!player.getRecipeBook().contains(recipe)) return;
                check("stock_recipe_unlocks_from_its_ingredients", true);
                minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe, false);
                stage = Stage.RESULT;
            }
            case RESULT -> {
                var result = menu.getSlot(0).getItem();
                if (!isRSConduit(result)) return;
                check("native_craft_yields_three_named_refined_storage_conduits", result.getCount() == 3
                    && result.getHoverName().getString().equals("Refined Storage Conduit"));
                capture("rs-conduit-recipe");
                minecraft.gameMode.handleInventoryMouseClick(menu.containerId, 0, 0, ClickType.QUICK_MOVE, player);
                stage = Stage.TAKEN;
            }
            case TAKEN -> {
                if (menu.getSlot(0).hasItem() || menu.slots.subList(1, 10).stream().anyMatch(Slot::hasItem) || conduitCount() != 3) return;
                check("survival_player_receives_all_three_crafted_conduits", menu.getCarried().isEmpty());
                player.closeContainer();
                signal("crafted");
                stage = Stage.PLACE_CONDUIT;
            }
            default -> { }
        }
    }

    private void placeConduits() throws IOException {
        var player = minecraft.player;
        var target = RS_HEAD.east(placedConduits);
        if (stage == Stage.PLACE_CONDUIT) {
            if (!ready("server-place-conduits") || minecraft.screen != null || player.distanceToSqr(1087.5, 80, 15.5) > 0.15) return;
            var inventory = player.getInventory();
            for (int slot = 0; slot < 9; slot++) {
                if (isRSConduit(inventory.getItem(slot))) {
                    inventory.selected = slot;
                    break;
                }
            }
            check("uses_a_crafted_conduit_" + placedConduits, isRSConduit(player.getMainHandItem()));
            var hit = new BlockHitResult(Vec3.atBottomCenterOf(target), Direction.UP, target.below(), false);
            check("native_conduit_placement_" + placedConduits, minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction());
            stage = Stage.PLACED_CONDUIT;
        } else if (minecraft.level.getBlockEntity(target) instanceof ConduitBundleBlockEntity bundle
            && bundle.getConduits().stream().anyMatch(holder -> holder.is(RS_CONDUIT)) && conduitCount() == 2 - placedConduits) {
            check("places_one_conduit_from_the_crafted_stack_" + placedConduits, true);
            if (++placedConduits == 2) {
                capture("rs-crafted-route");
                signal("conduits-placed");
                stage = Stage.OPEN;
            } else {
                stage = Stage.PLACE_CONDUIT;
            }
        }
    }

    private static boolean isRSConduit(ItemStack stack) {
        return stack.is(EIOItems.CONDUIT.get()) && RS_CONDUIT.equals(EnderIODataComponents.CONDUIT.getId(stack));
    }

    private int conduitCount() {
        return minecraft.player.getInventory().items.stream().filter(ClientStorageTerminalSmoke::isRSConduit).mapToInt(ItemStack::getCount).sum();
    }

    private void open() {
        var pos = route.pos();
        if (minecraft.screen != null || minecraft.player.distanceToSqr(pos.getX() - 1.5, 80, pos.getZ() + 0.5) > 0.1) return;
        if (arrivalTick < 0) arrivalTick = minecraft.player.tickCount;
        if (minecraft.player.tickCount - arrivalTick < 6) return;
        minecraft.player.getInventory().selected = 8;
        if (route == RS && ready("server-rs-place") && minecraft.level.getBlockState(pos).isAir()) {
            var stack = minecraft.player.getInventory().getSelected();
            if (stack.getCount() != 1 || !GRID_ITEM.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) return;
            var placement = new BlockHitResult(Vec3.atBottomCenterOf(pos), Direction.UP, pos.below(), false);
            check("rs_native_survival_grid_placement", minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                placement).consumesAction());
            stage = Stage.PLACING_GRID;
            return;
        }
        if (!ready("server-" + route.name) || minecraft.level.getBlockState(pos).isAir()) return;
        var hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.WEST, route == AE ? 0.375 : 0.5),
            Direction.WEST, pos, false);
        check(route.name + "_native_block_use_opens_menu", minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit).consumesAction());
        stage = Stage.INITIAL;
    }

    private void useTerminal() throws IOException {
        if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen)) return;
        var menu = screen.getMenu();
        var entry = findEntry(screen);
        if (entry == null) return;
        switch (stage) {
            case INITIAL -> {
                if (entry.quantity != initial) return;
                if (guide && terminalBeforeGuide == null) {
                    openGuide(screen);
                    return;
                }
                if (guide) check("guide_returns_to_the_same_terminal_and_balance", screen == terminalBeforeGuide);
                check(route.name + "_screen_syncs_tagged_count", menu.getCarried().isEmpty());
                capture(route.name + (reopen ? "-reopened" : "-initial"));
                if (reopen) {
                    check(route.name + "_player_retains_exact_withdrawal", sample(minecraft.player.getInventory().getItem(route.destination), HALF));
                    if (!stock) {
                        advance();
                        return;
                    }
                }
                check(route.name + "_exact_tagged_offer_is_in_hotbar", sample(minecraft.player.getInventory().getItem(offerSlot), offer));
                clickHotbar(menu, offerSlot);
                stage = Stage.PICKUP;
            }
            case PICKUP -> {
                if (!sample(menu.getCarried(), offer) || !clickEntry(screen, entry, 0)) return;
                stage = Stage.DEPOSITED;
            }
            case DEPOSITED -> {
                if (entry.quantity != initial + offer || !menu.getCarried().isEmpty()) return;
                if (!ready("server-" + route.name + "-deposited")) {
                    if (!Files.exists(output.resolve("signals/" + route.name + "-deposited.json"))) {
                        check(route.name + "_native_deposit_updates_open_count", minecraft.player.getInventory().getItem(offerSlot).isEmpty());
                        capture(route.name + "-deposited");
                        signal(route.name + "-deposited");
                    }
                    return;
                }
                if (clickEntry(screen, entry, 1)) stage = Stage.WITHDRAWING;
            }
            case WITHDRAWING -> {
                int destination = withdrawalSlot();
                if (entry.quantity != withdrawal || !sample(menu.getCarried(), withdrawal)) return;
                check(route.name + "_right_click_returns_half_with_exact_tag", minecraft.player.getInventory().getItem(destination).isEmpty());
                capture(route.name + "-withdrawn");
                clickHotbar(menu, destination);
                stage = Stage.STORING;
            }
            case STORING -> {
                if (menu.getCarried().isEmpty() && sample(minecraft.player.getInventory().getItem(withdrawalSlot()), withdrawal)) {
                    check(route.name + "_native_player_slot_keeps_the_withdrawal", entry.quantity == withdrawal);
                    advance();
                }
            }
            default -> { }
        }
    }

    private void openGuide(AbstractContainerScreen<?> terminal) {
        var button = terminal.children().stream().filter(OpenGuideButton.class::isInstance).map(OpenGuideButton.class::cast)
            .filter(candidate -> candidate.visible && candidate.active).findFirst().orElse(null);
        if (button == null) return;
        terminalBeforeGuide = terminal;
        check("native_terminal_help_button_opens", terminal.mouseClicked(button.getX() + button.getWidth() / 2.0,
            button.getY() + button.getHeight() / 2.0, 0));
        stage = Stage.GUIDE_PAGE;
    }

    private void verifyGuide() throws ReflectiveOperationException, IOException {
        var screen = minecraft.screen;
        if (screen == null || !screen.getClass().getName().equals("guideme.internal.screen.GuideScreen")) return;
        int tick = minecraft.player.tickCount;
        if (guideTick < 0) guideTick = tick;
        if (tick - guideTick < 3) return;
        var type = screen.getClass();
        var page = type.getMethod("getCurrentPageId").invoke(screen);
        check("guideme_opens_ae2s_original_terminals_page", TERMINAL_GUIDE.equals(page));
        if (stage == Stage.GUIDE_PAGE) {
            // Keep the optional GuideME implementation off the core QA classpath.
            var collection = type.getMethod("getGuide").invoke(screen);
            var pages = Class.forName("guideme.PageCollection", false, type.getClassLoader());
            check("terminals_page_comes_from_the_registered_ae2_guide", Boolean.TRUE.equals(
                pages.getMethod("pageExists", ResourceLocation.class).invoke(collection, TERMINAL_GUIDE)));
            var bounds = type.getMethod("getDocumentViewport").invoke(screen);
            int height = (Integer) bounds.getClass().getMethod("height").invoke(bounds);
            check("guideme_lays_out_the_document_viewport", height > 0);
            guideObservation.put("page", page.toString());
            guideObservation.put("viewportHeight", height);
            check("guideme_retains_the_open_terminal_as_its_parent", type.getMethod("getReturnToOnClose").invoke(screen) == terminalBeforeGuide);
            originalScroll = documentYAtOrigin(screen);
            capture("ae-terminal-guide");
            check("native_guide_scroll_is_consumed", screen.mouseScrolled(screen.width * 0.72, screen.height * 0.65, -4));
            guideTick = tick;
            stage = Stage.GUIDE_SCROLLED;
        } else {
            int scroll = documentYAtOrigin(screen) - originalScroll;
            check("native_guide_scroll_moves_the_document", scroll > 0);
            guideObservation.put("documentScrollDelta", scroll);
            capture("ae-terminal-guide-scrolled");
            check("escape_closes_the_native_guide", screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0));
            stage = Stage.INITIAL;
        }
    }

    private static int documentYAtOrigin(Screen screen) throws ReflectiveOperationException {
        var point = screen.getClass().getMethod("getDocumentPointUnclamped", double.class, double.class).invoke(screen, 0.0, 0.0);
        return (Integer) point.getClass().getMethod("y").invoke(point);
    }

    private int withdrawalSlot() {
        return stock && reopen ? 6 : route.destination;
    }

    private Entry findEntry(AbstractContainerScreen<?> screen) {
        if (route == AE && screen instanceof MEStorageScreen<?> terminal && terminal.getMenu().isPowered()) {
            for (var slot : terminal.getMenu().slots) {
                if (slot instanceof RepoSlot repo && repo.getEntry() != null && repo.getEntry().getWhat() instanceof AEItemKey key
                    && tagged(key.toStack())) {
                    return new Entry(repo.getStoredAmount(), screen.getGuiLeft() + slot.x + 8, screen.getGuiTop() + slot.y + 8, -1);
                }
            }
        }
        if (route == RS && screen instanceof GridScreen grid && grid.getGrid().isGridActive()) {
            var stacks = grid.getView().getStacks();
            int offset = grid.getCurrentOffset() * 9;
            for (int i = offset; i < Math.min(stacks.size(), offset + 9 * grid.getVisibleRows()); i++) {
                if (stacks.get(i) instanceof ItemGridStack item && tagged(item.getStack())) {
                    int visible = i - offset;
                    return new Entry(item.getQuantity(), screen.getGuiLeft() + 16 + visible % 9 * 18,
                        screen.getGuiTop() + 27 + visible / 9 * 18, i);
                }
            }
        }
        return null;
    }

    private boolean clickEntry(AbstractContainerScreen<?> screen, Entry entry, int button) {
        if (screen instanceof GridScreen grid) {
            // The grid hit-tests using the previous rendered cursor position. Only change the hidden client's internal cursor.
            var window = minecraft.getWindow();
            ObfuscationReflectionHelper.setPrivateValue(MouseHandler.class, minecraft.mouseHandler,
                entry.x * window.getScreenWidth() / window.getGuiScaledWidth(), "f_91507_");
            ObfuscationReflectionHelper.setPrivateValue(MouseHandler.class, minecraft.mouseHandler,
                entry.y * window.getScreenHeight() / window.getGuiScaledHeight(), "f_91508_");
            if (grid.getSlotNumber() != entry.index) return false;
        }
        check(route.name + (button == 0 ? "_storage_click_deposits" : "_storage_click_withdraws"),
            screen.mouseClicked(entry.x, entry.y, button));
        screen.mouseReleased(entry.x, entry.y, button);
        return true;
    }

    private void clickHotbar(AbstractContainerMenu menu, int inventorySlot) {
        var slot = menu.slots.stream().filter(candidate -> candidate.container == minecraft.player.getInventory()
            && candidate.getContainerSlot() == inventorySlot).findFirst().orElseThrow();
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.PICKUP, minecraft.player);
    }

    private boolean tagged(ItemStack stack) {
        return stack.is(Items.IRON_INGOT) && stack.hasTag() && stack.getTag().getInt("PortRoute") == route.marker;
    }

    private boolean sample(ItemStack stack, int count) {
        return tagged(stack) && stack.getCount() == count;
    }

    private void advance() throws IOException {
        minecraft.player.closeContainer();
        signal(route.name + "-done");
        if (route == AE && !guide) {
            route = RS;
            arrivalTick = -1;
            stage = Stage.OPEN;
        } else {
            stage = Stage.VERIFIED;
        }
    }

    private boolean ready(String name) {
        return Files.exists(output.resolve(name));
    }

    private void signal(String name) throws IOException {
        var directory = output.resolve("signals");
        Files.createDirectories(directory);
        var staging = directory.resolve(name + ".tmp");
        Files.writeString(staging, "{}");
        Files.move(staging, directory.resolve(name + ".json"));
    }

    private void capture(String name) throws IOException {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_silent_hidden_frame", Boolean.TRUE.equals(frame.get("passed")));
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("checks", checks);
        result.put("captures", captures);
        if (guide) result.put("guideObservation", guideObservation);
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_storage_terminals", result);
    }
}
