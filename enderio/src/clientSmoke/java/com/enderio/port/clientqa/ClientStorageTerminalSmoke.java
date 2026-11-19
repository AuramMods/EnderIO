package com.enderio.port.clientqa;

import appeng.api.stacks.AEItemKey;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
import com.refinedmods.refinedstorage.screen.grid.GridScreen;
import com.refinedmods.refinedstorage.screen.grid.stack.ItemGridStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class ClientStorageTerminalSmoke {
    private enum Stage { TITLE, JOIN, OPEN, PLACING_GRID, INITIAL, PICKUP, DEPOSITED, WITHDRAWING, STORING, VERIFIED, DONE }

    private record Route(String name, int marker, int destination) {
        BlockPos pos() { return new BlockPos(1085, 80, marker); }
    }

    private record Entry(long quantity, double x, double y, int index) { }

    private static final Route AE = new Route("ae", 4, 1);
    private static final Route RS = new Route("rs", 13, 2);
    private static final ResourceLocation GRID_ITEM = ResourceLocation.fromNamespaceAndPath("refinedstorage", "grid");
    private static final int BEFORE = 37;
    private static final int OFFER = 5;
    private static final int HALF = (BEFORE + OFFER) / 2;

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final String address = System.getProperty("enderio.clientqa.server");
    private final boolean reopen = System.getProperty("enderio.clientqa.multiplayerPhase").equals("verify");
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private long titleAt;
    private int arrivalTick = -1;
    private Stage stage = Stage.TITLE;
    private Route route = AE;

    ClientStorageTerminalSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen screen) {
                throw new AssertionError("Disconnected during " + route.name + " " + stage + " with " + screen.getTitle().getString());
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
                    stage = Stage.OPEN;
                }
                case OPEN -> open();
                case PLACING_GRID -> {
                    if (minecraft.player.getInventory().getItem(8).isEmpty() && !minecraft.level.getBlockState(RS.pos()).isAir()) {
                        signal("rs-placed");
                        stage = Stage.OPEN;
                    }
                }
                case INITIAL, PICKUP, DEPOSITED, WITHDRAWING, STORING -> useTerminal();
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
                if (entry.quantity != (reopen ? HALF : BEFORE)) return;
                check(route.name + "_screen_syncs_tagged_count", menu.getCarried().isEmpty());
                capture(route.name + (reopen ? "-reopened" : "-initial"));
                if (reopen) {
                    check(route.name + "_player_retains_exact_withdrawal", sample(minecraft.player.getInventory().getItem(route.destination), HALF));
                    advance();
                } else {
                    check(route.name + "_five_tagged_ingots_are_in_hotbar", sample(minecraft.player.getInventory().getItem(0), OFFER));
                    clickHotbar(menu, 0);
                    stage = Stage.PICKUP;
                }
            }
            case PICKUP -> {
                if (!sample(menu.getCarried(), OFFER) || !clickEntry(screen, entry, 0)) return;
                stage = Stage.DEPOSITED;
            }
            case DEPOSITED -> {
                if (entry.quantity != BEFORE + OFFER || !menu.getCarried().isEmpty()) return;
                if (!ready("server-" + route.name + "-deposited")) {
                    if (!Files.exists(output.resolve("signals/" + route.name + "-deposited.json"))) {
                        check(route.name + "_native_deposit_updates_open_count", minecraft.player.getInventory().getItem(0).isEmpty());
                        capture(route.name + "-deposited");
                        signal(route.name + "-deposited");
                    }
                    return;
                }
                if (clickEntry(screen, entry, 1)) stage = Stage.WITHDRAWING;
            }
            case WITHDRAWING -> {
                if (entry.quantity != HALF || !sample(menu.getCarried(), HALF)) return;
                check(route.name + "_right_click_returns_half_with_exact_tag", minecraft.player.getInventory().getItem(route.destination).isEmpty());
                capture(route.name + "-withdrawn");
                clickHotbar(menu, route.destination);
                stage = Stage.STORING;
            }
            case STORING -> {
                if (menu.getCarried().isEmpty() && sample(minecraft.player.getInventory().getItem(route.destination), HALF)) {
                    check(route.name + "_native_player_slot_keeps_the_withdrawal", entry.quantity == HALF);
                    advance();
                }
            }
            default -> { }
        }
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
        if (route == AE) {
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
        result.put("passed", failure == null);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_storage_terminals", result);
    }
}
